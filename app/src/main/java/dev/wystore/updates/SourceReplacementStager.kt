package dev.wystore.updates

import android.content.Context
import dev.wystore.data.*
import dev.wystore.data.library.*
import dev.wystore.data.local.UpdateQueueEntity
import dev.wystore.data.local.WyStoreDatabase
import dev.wystore.updates.model.QueueErrorCode
import dev.wystore.updates.model.QueueState
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class StagedSourceReplacement(val queueId: String, val identity: ArchiveIdentity, val choice: AppSourceChoice, val sourceRevision: Long)

/** Download and verify the replacement before the application can offer Android's removal dialog. */
class SourceReplacementStager(private val context: Context) {
    private val repository = StoreRepository(context)
    private val queue = QueueRepository.getInstance(context)
    private val dao = WyStoreDatabase.getInstance(context).updateQueueDao

    suspend fun stage(choice: AppSourceChoice, expectedRevision: Long): StagedSourceReplacement {
        var stagedForCleanup: StagedSourceReplacement? = null
        return try {
            withContext(Dispatchers.IO) {
                val pkg = requireNotNull(choice.packageName)
                requireRevision(pkg, expectedRevision)
                val directory = File(context.cacheDir, "source_replacement_${UUID.randomUUID()}")
                check(directory.mkdirs())
                try {
                    val downloader = SecureArtifactDownloader(context)
                    var signature: String? = null
                    var label = pkg
                    var releaseId: Long? = null
                    var libraryVersion: ResolvedLibraryVersion? = null
                    var libraryApplication: LibraryApplication? = null
                    var sourceVersionCode: Long? = null
                    val files = when (choice) {
                        is AppSourceChoice.Library -> {
                            val app = LibraryRepository.getInstance(context).index(forceRefresh = true).requireFresh()
                                .applications[pkg] ?: throw LibraryApplicationMissing(pkg)
                            val version = (LibraryVariantPolicy.select(app, LibraryDeviceSpec.current(context)) as? LibrarySelection.Compatible)
                                ?.version ?: throw TransferFailure(false, QueueErrorCode.INCOMPATIBLE)
                            signature = version.preliminaryCertificates(android.os.Build.VERSION.SDK_INT).singleOrNull()
                            libraryVersion = version
                            libraryApplication = app
                            label = app.name
                            version.artifacts.map { artifact ->
                                downloader.downloadGitHubApk(GitHubAsset(0, artifact.name, artifact.size, artifact.url, "sha256:${artifact.sha256}"),
                                    File(directory, artifact.sha256), requiresExactSelection = true)
                            }.also { LibraryFileIntegrity.requireMatches(it, version.artifacts) }
                        }
                        is AppSourceChoice.RuStore -> {
                            val source = RuStoreSource.getInstance(context)
                            val app = source.details(pkg, false)
                            signature = app.signatureHint; label = app.name
                            sourceVersionCode = app.versionCode.takeIf { it > 0 }
                            downloader.download(source.resolveArtifacts(app), directory)
                        }
                        is AppSourceChoice.GitHub -> {
                            val entry = GitHubCatalog.find(choice.repository.displayName)
                            val release = GitHubReleasePolicy.selectRelease(GitHubReleaseSource(context).releases(choice.repository), entry?.assetPattern())
                                ?: throw TransferFailure(false, QueueErrorCode.SOURCE_CHANGED)
                            val asset = GitHubReleasePolicy.preferredAsset(GitHubReleasePolicy.apkAssets(release, entry?.assetPattern()),
                                android.os.Build.SUPPORTED_ABIS.toList(), android.os.Build.VERSION.SDK_INT) ?: throw TransferFailure(false, QueueErrorCode.SOURCE_CHANGED)
                            releaseId = release.id; label = entry?.title ?: choice.repository.name
                            listOf(downloader.downloadGitHubApk(asset, directory, requiresExactSelection = true))
                        }
                    }
                    val verification = SigningVerifier.verifyArtifacts(context.packageManager, files, installed = null,
                        expectedPackageName = pkg, expectedSourceDigest = signature, allowReinstall = true)
                    val plan = verification.plan ?: throw TransferFailure(false, QueueErrorCode.SIGNATURE, detail = verification.error?.name)
                    if (libraryVersion?.let { !LibraryArchivePolicy.matches(it, plan.identity, plan.files.size) } == true ||
                        sourceVersionCode?.let { it != plan.identity.versionCode } == true) {
                        throw TransferFailure(false, QueueErrorCode.INTEGRITY, detail = "Replacement archive differs from source metadata")
                    }
                    val hashes = plan.files.map(LibraryFileIntegrity::sha256)
                    SourceSelectionGate.commit {
                        requireRevision(pkg, expectedRevision)
                        check(PendingReinstallStore(context).read(pkg) == null) { "An application handover is pending" }
                        val rows = dao.getByPackage(pkg)
                        if (SourceRequestPolicy.blocksSwitch(rows.map { QueueState.valueOf(it.state) }, rows.mapNotNull { dao.getSession(it.id)?.state })) {
                            throw TransferFailure(false, QueueErrorCode.SOURCE_CHANGED, detail = "Another installation is still active")
                        }
                        val revision = repository.advanceSourceRevision(pkg)
                        repository.managedApps().firstOrNull { it.packageName == pkg }?.let { managed ->
                            repository.updateManaged(pkg) { current -> requireNotNull(current).copy(sourceRevision = revision) }
                        }
                        for (row in rows) {
                            InstallSessionWriter(context).abandon(row.id)
                            dao.update(row.copy(sourceRevision = revision,
                                libraryRequestJson = row.libraryRequestJson?.let { LibraryRequestCodec.withRevision(it, revision) },
                                state = if (row.state == QueueState.READY_TO_INSTALL.name)
                                row.state else QueueState.CANCELED.name))
                            dev.wystore.background.TransferDispatcher.cancel(context, row.id)
                        }
                        val update = queue.enqueueReadyUpdate(plan = plan, source = choice.source, label = label,
                            githubRepository = (choice as? AppSourceChoice.GitHub)?.repository, githubReleaseId = releaseId,
                            sourceReplacement = true, sourceRevision = revision, sourceHashes = hashes,
                            libraryRequestJson = libraryApplication?.let { LibraryRequestCodec.encode(it, requireNotNull(libraryVersion).variantId, revision) })
                        val saved = dao.find(update.packageName, update.versionCode, update.source.name)
                            ?: error("Staged replacement was not persisted")
                        StagedSourceReplacement(saved.id, verification.identity, choice, revision).also { stagedForCleanup = it }
                    }
                } finally { directory.deleteRecursively() }
            }
        } catch (cancel: kotlinx.coroutines.CancellationException) {
            withContext(kotlinx.coroutines.NonCancellable) { stagedForCleanup?.let { discard(it.queueId) } }
            throw cancel
        }
    }

    suspend fun validateBeforeRemoval(queueId: String): UpdateQueueEntity = withContext(Dispatchers.IO) {
        val row = dao.getById(queueId) ?: throw TransferFailure(false, QueueErrorCode.SOURCE_CHANGED)
        SourceSelectionCoordinator.requireCurrent(context, row, pendingRemoval = true)
        if (!row.sourceReplacement || row.state != QueueState.READY_TO_INSTALL.name) {
            throw TransferFailure(false, QueueErrorCode.SOURCE_CHANGED)
        }
        val artifacts = dao.getArtifactsForQueue(queueId)
        val files = if (row.source == ManagedSource.WY_STORE.name) QueueArtifactIntegrity.requireMatches(row, artifacts)
        else artifacts.map { File(it.path) }.also { paths -> LibraryFileIntegrity.requireMatches(paths, artifacts.map {
            LibraryArtifact("", it.sourceHash ?: throw TransferFailure(false, QueueErrorCode.INTEGRITY), it.size, "")
        }) }
        val verification = SigningVerifier.verifyArtifacts(context.packageManager, files,
            installed = null, expectedPackageName = row.packageName, allowReinstall = true)
        if (!verification.isValid || verification.identity.versionCode != row.versionCode) {
            throw TransferFailure(false, QueueErrorCode.SIGNATURE)
        }
        row
    }

    suspend fun restore(queueId: String): StagedSourceReplacement = withContext(Dispatchers.IO) {
        val row = validateBeforeRemoval(queueId)
        val files = dao.getArtifactsForQueue(queueId).map { File(it.path) }
        val identity = SigningVerifier.verifyArtifacts(context.packageManager, files, installed = null,
            expectedPackageName = row.packageName, allowReinstall = true).identity
        val choice = when (ManagedSource.valueOf(row.source)) {
            ManagedSource.RUSTORE -> AppSourceChoice.RuStore(row.packageName)
            ManagedSource.WY_STORE -> AppSourceChoice.Library(row.packageName)
            ManagedSource.GITHUB -> AppSourceChoice.GitHub(GitHubRepository(requireNotNull(row.githubRepositoryOwner),
                requireNotNull(row.githubRepositoryName)), row.packageName)
        }
        StagedSourceReplacement(row.id, identity, choice, row.sourceRevision)
    }

    private fun requireRevision(packageName: String, expected: Long) {
        if (repository.sourceRevision(packageName) != expected) throw TransferFailure(false, QueueErrorCode.SOURCE_CHANGED)
    }

    suspend fun discard(queueId: String) = SourceSelectionGate.locked {
        val row = dao.getById(queueId) ?: return@locked
        if (row.sourceReplacement && row.state == QueueState.READY_TO_INSTALL.name &&
            PendingReinstallStore(context).read(row.packageName)?.queueId != queueId) {
            dao.update(row.copy(state = QueueState.CANCELED.name, updatedAt = System.currentTimeMillis()))
        }
    }
}
