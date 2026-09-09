package dev.wystore.updates

import android.content.Context
import dev.wystore.data.GitHubRepository
import dev.wystore.data.EventLog
import dev.wystore.data.ManagedSource
import dev.wystore.data.ArchiveIdentity
import dev.wystore.data.PendingUpdate
import dev.wystore.data.VerifiedInstallPlan
import dev.wystore.data.local.UpdateArtifactEntity
import dev.wystore.data.local.UpdateQueueDao
import dev.wystore.data.local.UpdateQueueEntity
import dev.wystore.data.local.WyStoreDatabase
import dev.wystore.data.local.toSnapshot
import dev.wystore.updates.model.QueueAction
import dev.wystore.updates.model.QueueErrorCode
import dev.wystore.updates.model.QueueItemSnapshot
import dev.wystore.updates.model.QueueState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class QueueRepository(
    private val context: Context,
    private val database: WyStoreDatabase = WyStoreDatabase.getInstance(context),
    private val cleanupPolicy: ArtifactCleanupPolicy = ArtifactCleanupPolicy()
) {
    private val dao: UpdateQueueDao get() = database.updateQueueDao
    private val rootDirectory = File(context.filesDir, "pending_updates")
    private val legacyStore = PendingUpdateStore(context)

    init {
        rootDirectory.mkdirs()
    }

    suspend fun importLegacyIfNeeded() = withContext(Dispatchers.IO) {
        if (legacyStore.isMigrated()) return@withContext
        val legacyItems = legacyStore.readLegacy()
        for (legacy in legacyItems) {
            val existing = dao.find(legacy.packageName, legacy.versionCode, legacy.source.name)
            val queueId = existing?.id ?: UUID.randomUUID().toString()
            val entity = UpdateQueueEntity(
                id = queueId,
                packageName = legacy.packageName,
                label = legacy.label,
                versionName = legacy.versionName,
                versionCode = legacy.versionCode,
                source = legacy.source.name,
                state = QueueState.READY_TO_INSTALL.name,
                priority = 0,
                position = 0,
                signingDigests = legacy.signingDigests.joinToString(","),
                githubRepositoryOwner = legacy.githubRepository?.owner,
                githubRepositoryName = legacy.githubRepository?.name,
                githubReleaseId = legacy.githubReleaseId,
                createdAt = legacy.downloadedAt,
                updatedAt = System.currentTimeMillis()
            )
            dao.upsertAtomic(entity)
            val artifactEntities = legacy.filePaths.mapNotNull { path ->
                val file = File(path)
                if (file.exists() && file.length() > 0L) {
                    UpdateArtifactEntity(
                        queueId = queueId,
                        path = file.absolutePath,
                        size = file.length(),
                        createdAt = legacy.downloadedAt,
                        lastAccessedAt = System.currentTimeMillis()
                    )
                } else null
            }
            if (artifactEntities.isNotEmpty()) {
                dao.insertArtifacts(artifactEntities)
            }
        }
        legacyStore.markMigrated()
    }

    fun observeAll(): Flow<List<QueueItemSnapshot>> =
        dao.observeAll().map { entities ->
            entities.map { it.toSnapshot() }
        }.distinctUntilChanged()

    fun observePendingUpdates(): Flow<List<PendingUpdate>> =
        dao.observeAll().map { entities ->
            withContext(Dispatchers.IO) {
                entities.filter { it.state in INSTALLABLE_STATES }
                    .mapNotNull { entity -> entityToPendingUpdate(entity) }
            }
        }.distinctUntilChanged()

    suspend fun getPendingUpdates(): List<PendingUpdate> = withContext(Dispatchers.IO) {
        importLegacyIfNeeded()
        dao.getAll()
            .filter { it.state in INSTALLABLE_STATES }
            .mapNotNull { entityToPendingUpdate(it) }
    }

    /**
     * Items parked waiting for the user to grant install-from-unknown-sources. This lives in the
     * database rather than an Activity field so the install can be resumed after the user comes
     * back from Android Settings, even if the process was killed while they were away.
     */
    suspend fun awaitingUnknownSources(): List<PendingUpdate> = withContext(Dispatchers.IO) {
        dao.getAll()
            .filter { it.state == QueueState.AWAITING_UNKNOWN_SOURCES_PERMISSION.name }
            .mapNotNull { entityToPendingUpdate(it) }
    }

    suspend fun markAwaitingUnknownSources(packageName: String) = withContext(Dispatchers.IO) {
        val entity = dao.getByPackage(packageName)
            .firstOrNull { it.state == QueueState.READY_TO_INSTALL.name } ?: return@withContext
        transition(entity.id, QueueAction.AwaitUnknownSourcesPermission)
        Unit
    }

    /**
     * Called once the permission is actually held. The row goes back to READY_TO_INSTALL so the
     * normal install path can re-run its own verification from a known state.
     */
    suspend fun clearAwaitingUnknownSources(packageName: String) = withContext(Dispatchers.IO) {
        val entity = dao.getByPackage(packageName)
            .firstOrNull { it.state == QueueState.AWAITING_UNKNOWN_SOURCES_PERMISSION.name }
            ?: return@withContext
        resetReadyToInstall(entity.id)
        Unit
    }

    suspend fun transition(id: String, action: QueueAction): QueueItemSnapshot = withContext(Dispatchers.IO) {
        val entity = dao.getById(id) ?: throw IllegalArgumentException("Queue item not found: $id")
        val currentSnapshot = entity.toSnapshot()
        val nextSnapshot = QueueReducer.reduce(currentSnapshot, action)
        val updatedEntity = entity.copy(
            state = nextSnapshot.state.name,
            priority = nextSnapshot.priority,
            position = nextSnapshot.position,
            downloadedBytes = nextSnapshot.downloadedBytes,
            totalBytes = nextSnapshot.totalBytes,
            errorCode = nextSnapshot.errorCode?.name,
            errorDetail = nextSnapshot.errorDetail,
            updatedAt = System.currentTimeMillis()
        )
        if (nextSnapshot.state.name in UpdateQueueDao.ACTIVE_STATES && entity.state !in UpdateQueueDao.ACTIVE_STATES) {
            dao.transitionToActive(id, nextSnapshot.state.name)
        } else {
            dao.update(updatedEntity)
        }
        nextSnapshot
    }

    /**
     * Parks a retryable failure back in AVAILABLE while keeping the diagnosis visible.
     *
     * WorkManager reruns [dev.wystore.background.UpdateDownloadWorker.doWork] from the top, and its
     * first move is StartDownload, which the reducer only accepts from AVAILABLE or CHECKING.
     * Leaving the row in FAILED would make every retry throw on its first transition.
     */
    suspend fun resetForRetry(
        id: String,
        errorCode: QueueErrorCode?,
        errorDetail: String?
    ): QueueItemSnapshot? = withContext(Dispatchers.IO) {
        val entity = dao.getById(id) ?: return@withContext null
        if (errorCode != null) {
            runCatching { EventLog(context).record(entity.packageName, errorCode.name, errorDetail) }
        }
        val reset = entity.copy(
            state = QueueState.AVAILABLE.name,
            downloadedBytes = 0L,
            totalBytes = 0L,
            errorCode = errorCode?.name,
            errorDetail = errorDetail,
            updatedAt = System.currentTimeMillis()
        )
        dao.update(reset)
        reset.toSnapshot()
    }

    /**
     * Records a terminal failure. Unlike [transition] this tolerates a row that is already in a
     * terminal state, so a late worker callback cannot crash on an illegal transition.
     */
    suspend fun markFailed(
        id: String,
        errorCode: QueueErrorCode,
        errorDetail: String?
    ): QueueItemSnapshot? = withContext(Dispatchers.IO) {
        val entity = dao.getById(id) ?: return@withContext null
        val failed = entity.copy(
            state = QueueState.FAILED.name,
            errorCode = errorCode.name,
            errorDetail = errorDetail,
            updatedAt = System.currentTimeMillis()
        )
        dao.update(failed)
        // Kept for the diagnostics report: the row itself is gone the moment it is retried or
        // cleared, and Logcat is not somewhere the person reporting a problem can reach.
        runCatching { EventLog(context).record(entity.packageName, errorCode.name, errorDetail) }
        failed.toSnapshot()
    }

    /**
     * Applies [action] only when the row is in one of [allowedFrom]. Used on the install path, where
     * the same step can be reached twice (a resumed Activity, a redelivered broadcast) and throwing
     * on the second attempt would abort a perfectly good install.
     */
    suspend fun transitionIfIn(
        id: String,
        allowedFrom: Set<QueueState>,
        action: QueueAction
    ): QueueItemSnapshot? = withContext(Dispatchers.IO) {
        val entity = dao.getById(id) ?: return@withContext null
        val current = runCatching { QueueState.valueOf(entity.state) }.getOrNull()
        if (current !in allowedFrom) return@withContext entity.toSnapshot()
        transition(id, action)
    }

    /**
     * Like [transitionIfIn], but answers "not now" instead of throwing when the single transfer
     * slot is taken. An install that has to wait for a download to finish is an ordinary thing to
     * happen; treating it as a failure is what put "Wy Store itself failed" on a working queue.
     */
    suspend fun transitionIfFree(
        id: String,
        allowedFrom: Set<QueueState>,
        action: QueueAction
    ): Boolean = try {
        transitionIfIn(id, allowedFrom, action)
        true
    } catch (busy: dev.wystore.data.local.QueueBusyException) {
        false
    }

    /**
     * Records the outcome of a PackageInstaller callback without depending on the row already being
     * INSTALLING. Callbacks arrive after process death, after a reboot, and for sessions this app no
     * longer tracks, so the outcome is written directly instead of being pushed through the reducer.
     */
    suspend fun reconcileInstallResult(
        id: String,
        success: Boolean,
        errorCode: QueueErrorCode = QueueErrorCode.INSTALL_FAILED,
        errorDetail: String? = null
    ): QueueItemSnapshot? = withContext(Dispatchers.IO) {
        val entity = dao.getById(id) ?: return@withContext null

        // Saying "not now" to Android's dialog is not a failed download. The verified APK is still
        // on disk, so the row goes back to waiting rather than to an error whose only offered
        // action is fetching the same two hundred megabytes again.
        if (!success && errorCode == QueueErrorCode.INSTALL_CANCELED) {
            val ready = resetReadyToInstall(id)
            if (ready?.state == QueueState.READY_TO_INSTALL) {
                dao.deleteSession(id)
                return@withContext ready
            }
        }
        // INSTALLED is terminal. Parking a finished install in OFFER_NEXT left a row nothing ever
        // moved on, and every screen that reads the queue kept treating the app as busy.
        val target = if (success) QueueState.INSTALLED else QueueState.FAILED
        // An install that Android refused never passes through markFailed, so without this the one
        // failure a user is most likely to be asking about was the one missing from the report.
        if (!success) {
            runCatching { EventLog(context).record(entity.packageName, errorCode.name, errorDetail) }
        }
        val updated = entity.copy(
            state = target.name,
            errorCode = if (success) null else errorCode.name,
            errorDetail = if (success) null else errorDetail,
            updatedAt = System.currentTimeMillis()
        )
        dao.update(updated)
        dao.deleteSession(id)
        // The APK has been consumed. It used to sit in private storage until some later download
        // happened to run the cleanup pass, so a phone that installed everything and then stopped
        // fetching kept every archive it had ever installed. The row stays - screens read it to
        // show the install finished - only the file goes. A declined install is the case above:
        // that one keeps its archive, so saying "not now" does not cost the download again.
        if (success) {
            for (artifact in dao.getArtifactsForQueue(id)) {
                runCatching { File(artifact.path).delete() }
            }
            dao.deleteArtifactsForQueue(id)
            runCatching { File(rootDirectory, id).deleteRecursively() }
        }
        updated.toSnapshot()
    }

    /**
     * Puts a downloaded item back into READY_TO_INSTALL after a handover to Android failed. The
     * artifacts are still verified and on disk, so the item must stay installable.
     */
    suspend fun resetReadyToInstall(id: String): QueueItemSnapshot? = withContext(Dispatchers.IO) {
        val entity = dao.getById(id) ?: return@withContext null
        if (dao.getArtifactsForQueue(id).none { File(it.path).isFile }) return@withContext entity.toSnapshot()
        val updated = entity.copy(
            state = QueueState.READY_TO_INSTALL.name,
            errorCode = null,
            errorDetail = null,
            updatedAt = System.currentTimeMillis()
        )
        dao.update(updated)
        dao.deleteSession(id)
        updated.toSnapshot()
    }

    /**
     * When the most recent install finished, or null when none has.
     *
     * OFFER_NEXT is included for rows written before installs became terminal; those are finished
     * installs too.
     */
    suspend fun lastFinishedInstallAt(): Long? = withContext(Dispatchers.IO) {
        dao.getAll()
            .filter { it.state == QueueState.INSTALLED.name || it.state == QueueState.OFFER_NEXT.name }
            .maxOfOrNull { it.updatedAt }
    }

    /**
     * Puts rows the user declined back into "downloaded, waiting for you".
     *
     * A declined install used to be recorded as an error, and rows written that way are still in
     * the database. Their verified APKs are on disk, so the only thing the error achieved was
     * offering to download all of it again.
     */
    suspend fun restoreDeclinedInstalls(): Int = withContext(Dispatchers.IO) {
        dao.getAll()
            .filter { it.errorCode == QueueErrorCode.INSTALL_CANCELED.name }
            .filter { it.state == QueueState.FAILED.name || it.state == QueueState.CANCELED.name }
            .count { entity -> resetReadyToInstall(entity.id)?.state == QueueState.READY_TO_INSTALL }
    }

    /** Every item whose verified artifacts are on disk, for the consolidated ready notification. */
    suspend fun readyToInstallSnapshots(): List<QueueItemSnapshot> = withContext(Dispatchers.IO) {
        dao.getAll()
            .filter { it.state == QueueState.READY_TO_INSTALL.name }
            .filter { entity -> dao.getArtifactsForQueue(entity.id).any { File(it.path).isFile } }
            .map { it.toSnapshot() }
    }

    /** Every row as it stands, for the diagnostics report. */
    suspend fun snapshotAll(): List<QueueItemSnapshot> = withContext(Dispatchers.IO) {
        dao.getAll().map { it.toSnapshot() }
    }

    /**
     * Items that stopped with an error and are still waiting for the user to do something about
     * them. Drives the single consolidated error notification.
     */
    suspend fun failedSnapshots(): List<QueueItemSnapshot> = withContext(Dispatchers.IO) {
        dao.getAll()
            .filter { it.state == QueueState.FAILED.name }
            .map { it.toSnapshot() }
    }

    /** Files persisted for a queue item that still exist on disk. */
    suspend fun artifactFilesFor(queueId: String): List<File> = withContext(Dispatchers.IO) {
        dao.getArtifactsForQueue(queueId)
            .map { File(it.path) }
            .filter { it.isFile && it.length() > 0L }
    }

    suspend fun observeAllOnce(): List<QueueItemSnapshot> = withContext(Dispatchers.IO) {
        dao.getAll().map { it.toSnapshot() }
    }

    suspend fun getById(id: String): QueueItemSnapshot? = withContext(Dispatchers.IO) {
        dao.getById(id)?.toSnapshot()
    }

    suspend fun getEntityById(id: String): UpdateQueueEntity? = withContext(Dispatchers.IO) {
        dao.getById(id)
    }

    /** Ids of the items holding the single transfer slot - empty when the queue is free to start. */
    suspend fun activeIds(): List<String> = withContext(Dispatchers.IO) {
        dao.getActive(UpdateQueueDao.ACTIVE_STATES).map { it.id }
    }

    suspend fun nextEligible(): QueueItemSnapshot? = withContext(Dispatchers.IO) {
        dao.nextEligible()?.toSnapshot()
    }

    /**
     * Records that everything waiting was asked for.
     *
     * "Start" is a request for the queue, not for its first row. Only that row was dispatched as a
     * user request; the ones behind it were left carrying the background priority, so the pump
     * applied the unattended constraints to them and a queue started by hand could stop dead
     * waiting for Wi-Fi or a charger the user had not been told about.
     */
    suspend fun markWaitingAsUserRequested() = withContext(Dispatchers.IO) {
        dao.raiseWaitingPriority(QueueOrigin.USER_REQUESTED_PRIORITY, System.currentTimeMillis())
    }

    /**
     * The next waiting row [allow] accepts, in the queue's own order.
     *
     * For starting the queue without being asked: [nextEligible] answers "what is next", which is
     * the right question for a button someone pressed and the wrong one for the queue passing the
     * turn to itself - an app the user excluded from auto-updates has to be stepped over there.
     */
    suspend fun nextEligible(allow: (QueueItemSnapshot) -> Boolean): QueueItemSnapshot? =
        withContext(Dispatchers.IO) {
            dao.eligible(ELIGIBLE_LOOKAHEAD).asSequence().map { it.toSnapshot() }.firstOrNull(allow)
        }

    /**
     * What the queue should offer next: something already downloaded before something that still
     * has to be fetched.
     */
    suspend fun nextToOffer(): QueueItemSnapshot? = withContext(Dispatchers.IO) {
        (dao.nextInstallable() ?: dao.nextEligible())?.toSnapshot()
    }

    /**
     * Persists a finished, verified download: the APK files move into private storage and the
     * identity that [dev.wystore.data.SigningVerifier] read out of them is written onto the queue
     * row in the same transaction. Install time re-reads the APKs and compares against exactly
     * these digests, so a row that reaches READY_TO_INSTALL without them can never be installed.
     */
    suspend fun saveVerifiedArtifacts(
        queueId: String,
        identity: ArchiveIdentity,
        files: List<File>,
        sourceHash: String? = null
    ): List<UpdateArtifactEntity> = withContext(Dispatchers.IO) {
        require(identity.signingDigests.isNotEmpty()) { "Verified identity carries no signing digest" }
        require(identity.packageName.isNotBlank()) { "Verified identity carries no package name" }
        require(files.isNotEmpty()) { "Verified APK set is empty" }
        val targetDir = File(rootDirectory, queueId).apply { mkdirs() }
        val copied = files.mapIndexed { index, file ->
            val dest = File(targetDir, "artifact_$index.apk")
            if (file.canonicalFile != dest.canonicalFile) {
                file.copyTo(dest, overwrite = true)
            }
            UpdateArtifactEntity(
                queueId = queueId,
                path = dest.absolutePath,
                size = dest.length(),
                sourceHash = sourceHash,
                createdAt = System.currentTimeMillis(),
                lastAccessedAt = System.currentTimeMillis()
            )
        }
        val obsolete = dao.saveVerifiedArtifacts(
            queueId = queueId,
            packageName = identity.packageName,
            versionName = identity.versionName,
            versionCode = identity.versionCode,
            signingDigests = identity.signingDigests.joinToString(","),
            artifacts = copied
        )
        val keptPaths = copied.map { it.path }.toSet()
        for (path in obsolete) {
            if (path !in keptPaths) File(path).delete()
        }
        copied
    }

    /**
     * Records the name the source publishes for an app.
     *
     * A manual install knows only a package name for an app the device does not have, and that is
     * what every queue row and every "ready to install" card then showed. The download step learns
     * the real name on its way past the source, so it is written down there.
     */
    suspend fun updateLabel(id: String, label: String) = withContext(Dispatchers.IO) {
        if (label.isBlank()) return@withContext
        val entity = dao.getById(id) ?: return@withContext
        if (entity.label == label) return@withContext
        dao.update(entity.copy(label = label, updatedAt = System.currentTimeMillis()))
    }

    suspend fun markAccessed(id: String) = withContext(Dispatchers.IO) {
        dao.updateQueueArtifactsLastAccessed(id, System.currentTimeMillis())
    }

    /**
     * Applies the user's retention and storage-quota settings.
     *
     * [cleanup] was implemented and unit-tested but never called from production, so the retention
     * and storage-limit settings had no effect and downloaded APKs accumulated indefinitely in
     * private storage. This is the production entry point.
     */
    suspend fun cleanupWithSettings(settings: dev.wystore.data.StoreSettings): List<String> = cleanup(
        now = System.currentTimeMillis(),
        quotaBytes = settings.artifactStorageLimitMb.coerceAtLeast(0).toLong() * 1024L * 1024L,
        retentionMillis = settings.artifactRetentionDays.coerceAtLeast(0).toLong() * 24L * 60L * 60L * 1000L
    )

    suspend fun cleanup(
        now: Long,
        quotaBytes: Long,
        retentionMillis: Long
    ): List<String> = withContext(Dispatchers.IO) {
        val allEntities = dao.getAll()
        val allArtifacts = dao.getAllArtifacts()
        val candidates = allEntities.mapNotNull { entity ->
            val artifacts = allArtifacts.filter { it.queueId == entity.id }
            if (artifacts.isEmpty()) return@mapNotNull null
            val state = runCatching { QueueState.valueOf(entity.state) }.getOrDefault(QueueState.AVAILABLE)
            val totalSize = artifacts.sumOf { it.size }
            val lastAccessed = artifacts.maxOfOrNull { it.lastAccessedAt } ?: entity.updatedAt
            ArtifactCleanupCandidate(
                id = entity.id,
                packageName = entity.packageName,
                versionCode = entity.versionCode,
                sizeBytes = totalSize,
                state = state,
                lastAccessedAt = lastAccessed,
                isCurrent = state == QueueState.READY_TO_INSTALL
            )
        }
        val toEvict = cleanupPolicy.select(candidates, now, quotaBytes, retentionMillis)
        val evictedPaths = mutableListOf<String>()
        for (candidate in toEvict) {
            val artifacts = dao.getArtifactsForQueue(candidate.id)
            dao.deleteById(candidate.id)
            for (art in artifacts) {
                val file = File(art.path)
                if (file.exists()) {
                    file.delete()
                    evictedPaths.add(art.path)
                }
            }
            File(rootDirectory, candidate.id).deleteRecursively()
        }
        evictedPaths
    }

    suspend fun enqueueReadyUpdate(
        plan: VerifiedInstallPlan,
        label: String,
        source: ManagedSource,
        githubRepository: GitHubRepository? = null,
        githubReleaseId: Long? = null
    ): PendingUpdate = withContext(Dispatchers.IO) {
        importLegacyIfNeeded()
        require(plan.files.isNotEmpty() && plan.files.all { it.isFile && it.length() > 0L }) {
            "A verified APK is no longer on disk"
        }
        val identity = plan.identity
        val existing = dao.find(identity.packageName, identity.versionCode, source.name)
        val queueId = existing?.id ?: UUID.randomUUID().toString()
        val targetDir = File(rootDirectory, queueId)
        if (!targetDir.exists()) {
            require(targetDir.mkdirs()) { "Could not create the artifact directory" }
        }
        val copiedFiles = try {
            plan.files.mapIndexed { index, sourceFile ->
                File(targetDir, "artifact_$index.apk").also { dest -> sourceFile.copyTo(dest, overwrite = true) }
            }
        } catch (e: Exception) {
            targetDir.deleteRecursively()
            throw e
        }
        val entity = UpdateQueueEntity(
            id = queueId,
            packageName = identity.packageName,
            label = label,
            versionName = identity.versionName,
            versionCode = identity.versionCode,
            source = source.name,
            state = QueueState.READY_TO_INSTALL.name,
            priority = 0,
            position = 0,
            signingDigests = identity.signingDigests.joinToString(","),
            githubRepositoryOwner = githubRepository?.owner,
            githubRepositoryName = githubRepository?.name,
            githubReleaseId = githubReleaseId,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        dao.upsertAtomic(entity)
        val artifacts = copiedFiles.map { file ->
            UpdateArtifactEntity(
                queueId = queueId,
                path = file.absolutePath,
                size = file.length(),
                createdAt = System.currentTimeMillis(),
                lastAccessedAt = System.currentTimeMillis()
            )
        }
        dao.insertArtifacts(artifacts)

        // Clean up older ready updates for this same package
        val otherItems = dao.getByPackage(identity.packageName).filter { it.id != queueId }
        for (old in otherItems) {
            val oldArtifacts = dao.getArtifactsForQueue(old.id)
            dao.deleteById(old.id)
            for (art in oldArtifacts) {
                File(art.path).delete()
            }
            File(rootDirectory, old.id).deleteRecursively()
        }

        PendingUpdate(
            packageName = identity.packageName,
            label = label,
            versionName = identity.versionName,
            versionCode = identity.versionCode,
            filePaths = copiedFiles.map { it.absolutePath },
            signingDigests = identity.signingDigests,
            source = source,
            githubRepository = githubRepository,
            githubReleaseId = githubReleaseId,
            downloadedAt = entity.createdAt
        )
    }

    suspend fun enqueueAvailableUpdate(entity: UpdateQueueEntity): UpdateQueueEntity = withContext(Dispatchers.IO) {
        dao.upsertAtomic(entity)
        entity
    }

    suspend fun enqueueAvailableUpdate(
        packageName: String,
        label: String,
        versionName: String,
        versionCode: Long,
        source: ManagedSource,
        priority: Int = 0,
        githubRepository: GitHubRepository? = null,
        githubReleaseId: Long? = null
    ): UpdateQueueEntity = withContext(Dispatchers.IO) {
        val existing = dao.find(packageName, versionCode, source.name)
        val entity = UpdateQueueEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            packageName = packageName,
            label = label,
            versionName = versionName,
            versionCode = versionCode,
            source = source.name,
            state = existing?.state ?: QueueState.AVAILABLE.name,
            priority = priority,
            position = existing?.position ?: 0,
            githubRepositoryOwner = githubRepository?.owner,
            githubRepositoryName = githubRepository?.name,
            githubReleaseId = githubReleaseId,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        dao.upsertAtomic(entity)
        entity
    }

    /**
     * Prepares the queue for a manual install or update of [packageName].
     *
     * Returns the row a transfer can legally start from, or null when the queue is already handling
     * the package. A stopped row is reused rather than left behind: enqueueing on top of a FAILED
     * row made the worker's first transition throw, which is why an app that had failed once could
     * never be installed again from its card.
     */
    suspend fun enqueueManualInstall(
        packageName: String,
        label: String,
        source: ManagedSource,
        priority: Int = 10
    ): UpdateQueueEntity? = withContext(Dispatchers.IO) {
        val rows = dao.getByPackage(packageName).filter { it.source == source.name }
        val states = rows.mapNotNull { runCatching { QueueState.valueOf(it.state) }.getOrNull() }
        if (ManualEnqueuePolicy.decide(states) == ManualEnqueueAction.IGNORE) return@withContext null

        val target = rows.maxByOrNull { it.versionCode }
        val prepared = if (target == null) {
            UpdateQueueEntity(
                id = UUID.randomUUID().toString(),
                packageName = packageName,
                label = label,
                versionName = "",
                versionCode = 0,
                source = source.name,
                state = QueueState.AVAILABLE.name,
                priority = priority,
                position = 0
            )
        } else {
            target.copy(
                // A label the user can read. Manual installs know only the package name at this
                // point for an app that is not installed, so a better one is kept if it is there.
                label = label.takeIf { it.isNotBlank() && it != packageName } ?: target.label,
                state = QueueState.AVAILABLE.name,
                priority = priority,
                downloadedBytes = 0L,
                totalBytes = 0L,
                errorCode = null,
                errorDetail = null,
                updatedAt = System.currentTimeMillis()
            )
        }
        if (target == null) dao.insertOrReplace(prepared) else dao.update(prepared)

        // Other stopped rows for the same package are noise: they keep an old failure visible on
        // every card that looks the package up.
        rows.filter { it.id != prepared.id }
            .filter { runCatching { QueueState.valueOf(it.state) }.getOrNull() !in ACTIVE_OR_READY }
            .filter { dao.getArtifactsForQueue(it.id).none { artifact -> File(artifact.path).isFile } }
            .forEach { dao.deleteById(it.id) }

        prepared
    }

    /**
     * Drops one row and whatever it downloaded.
     *
     * For an outcome that is not a failure and not an install: an archive that turns out to be no
     * newer than what is on the phone leaves nothing to do, so leaving the row behind means a red
     * card about an app that is perfectly up to date, plus its APK sitting on disk.
     */
    suspend fun discard(id: String) = withContext(Dispatchers.IO) {
        val artifacts = dao.getArtifactsForQueue(id)
        dao.deleteById(id)
        for (artifact in artifacts) {
            File(artifact.path).delete()
        }
        File(rootDirectory, id).deleteRecursively()
    }

    suspend fun remove(packageName: String) = withContext(Dispatchers.IO) {
        val items = dao.getByPackage(packageName)
        for (item in items) {
            val artifacts = dao.getArtifactsForQueue(item.id)
            dao.deleteById(item.id)
            for (art in artifacts) {
                File(art.path).delete()
            }
            File(rootDirectory, item.id).deleteRecursively()
        }
    }

    suspend fun hasPending(packageName: String): Boolean = withContext(Dispatchers.IO) {
        dao.getByPackage(packageName).any { it.state == QueueState.READY_TO_INSTALL.name }
    }

    suspend fun hasPending(repository: GitHubRepository, releaseId: Long?): Boolean = withContext(Dispatchers.IO) {
        dao.getAll().any {
            it.state == QueueState.READY_TO_INSTALL.name &&
                it.githubRepositoryOwner == repository.owner &&
                it.githubRepositoryName == repository.name &&
                it.githubReleaseId == releaseId
        }
    }

    private suspend fun entityToPendingUpdate(entity: UpdateQueueEntity): PendingUpdate? {
        val artifacts = dao.getArtifactsForQueue(entity.id)
        val validFiles = artifacts.map { File(it.path) }.filter { it.isFile && it.length() > 0L }
        if (validFiles.isEmpty()) return null
        val source = runCatching { ManagedSource.valueOf(entity.source) }.getOrDefault(ManagedSource.RUSTORE)
        val repo = if (entity.githubRepositoryOwner != null && entity.githubRepositoryName != null) {
            GitHubRepository(owner = entity.githubRepositoryOwner, name = entity.githubRepositoryName)
        } else null
        val digests = entity.signingDigests.split(",").filter { it.isNotBlank() }.toSet()
        return PendingUpdate(
            packageName = entity.packageName,
            label = entity.label,
            versionName = entity.versionName,
            versionCode = entity.versionCode,
            filePaths = validFiles.map { it.absolutePath },
            signingDigests = digests,
            source = source,
            githubRepository = repo,
            githubReleaseId = entity.githubReleaseId,
            downloadedAt = entity.createdAt
        )
    }

    companion object {
        /**
         * How far ahead the queue looks when it has to step over rows. Deep enough for a whole
         * round of updates, shallow enough that a stalled queue does not read the entire table.
         */
        private const val ELIGIBLE_LOOKAHEAD = 100

        private val ACTIVE_OR_READY = setOf(
            QueueState.CHECKING,
            QueueState.DOWNLOADING,
            QueueState.VERIFYING,
            QueueState.INSTALLING,
            QueueState.READY_TO_INSTALL,
            QueueState.AWAITING_UNKNOWN_SOURCES_PERMISSION,
            QueueState.AWAITING_USER_CONFIRMATION
        )

        /** States in which verified artifacts are on disk and the item can still be installed. */
        private val INSTALLABLE_STATES = setOf(
            QueueState.READY_TO_INSTALL.name,
            QueueState.AWAITING_UNKNOWN_SOURCES_PERMISSION.name,
            QueueState.AWAITING_USER_CONFIRMATION.name
        )

        @Volatile
        private var instance: QueueRepository? = null

        fun getInstance(context: Context): QueueRepository =
            instance ?: synchronized(this) {
                instance ?: QueueRepository(context.applicationContext).also { instance = it }
            }
    }
}
