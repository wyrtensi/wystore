package dev.wystore.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import android.os.Build
import dev.wystore.data.GitHubCatalog
import dev.wystore.data.GitHubReleasePolicy
import dev.wystore.data.GitHubReleaseSource
import dev.wystore.data.GitHubRepository
import dev.wystore.data.ManagedSource
import dev.wystore.data.RuStoreSource
import dev.wystore.data.SecureArtifactDownloader
import dev.wystore.data.SigningVerifier
import dev.wystore.data.StoreRepository
import dev.wystore.data.invalidateInstalledApps
import dev.wystore.data.classifyThrowable
import dev.wystore.root.RootInstaller
import dev.wystore.updates.GitHubInstallScheduler
import dev.wystore.updates.InstallMode
import dev.wystore.updates.InstallModePolicy
import dev.wystore.updates.QueueRepository
import dev.wystore.updates.model.QueueAction
import dev.wystore.updates.model.QueueState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

class UpdateDownloadWorker(
    appContext: Context,
    parameters: WorkerParameters
) : CoroutineWorker(appContext, parameters) {

    private val queueRepository = QueueRepository.getInstance(appContext)
    private val executor = TransferExecutor(appContext, queueRepository)

    override suspend fun doWork(): Result {
        val queueId = inputData.getString(KEY_QUEUE_ID) ?: return Result.failure()
        val item = queueRepository.getById(queueId) ?: return Result.failure()

        setForeground(DownloadForegroundInfoFactory.createForegroundInfo(applicationContext, item.label))

        return try {
            executor.execute(queueId)
            Result.success()
        } catch (cancellation: CancellationException) {
            runCatching { queueRepository.transition(queueId, QueueAction.Cancel) }
            runCatching { workingDirectory(queueId).deleteRecursively() }
            throw cancellation
        } catch (error: Throwable) {
            val failure = classifyThrowable(error)
            // A WorkManager retry re-enters doWork() and starts from StartDownload, which is only
            // legal from AVAILABLE/CHECKING. Parking a retryable failure in FAILED would make the
            // next attempt throw on its very first transition, so the row is reset to AVAILABLE
            // instead and only a terminal failure is recorded as FAILED.
            if (failure.retryable && runAttemptCount < MAX_RUN_ATTEMPTS) {
                runCatching { queueRepository.resetForRetry(queueId, failure.code, failure.detail) }
                Result.retry()
            } else {
                runCatching { queueRepository.markFailed(queueId, failure.code, failure.detail) }
                // Nothing will resume this, so the partial bytes are dead weight.
                runCatching { workingDirectory(queueId).deleteRecursively() }
                // A background download that died used to be silent: the error switch in Settings
                // guarded a method with no callers.
                runCatching {
                    NotificationCoordinator(applicationContext)
                        .publishErrors(queueRepository.failedSnapshots())
                }
                Result.failure()
            }
        }
    }

    class TransferExecutor(
        private val context: Context,
        private val queueRepository: QueueRepository
    ) {
        private val ruStoreSource = RuStoreSource.getInstance(context)
        private val gitHubSource = GitHubReleaseSource(context)
        private val downloader = SecureArtifactDownloader(context)
        private val coordinator = NotificationCoordinator(context)
        private val storeRepository = StoreRepository(context)

        suspend fun execute(queueId: String) = withContext(Dispatchers.IO) {
            val entity = queueRepository.getEntityById(queueId)
                ?: throw IllegalArgumentException("Queue item not found: $queueId")

            queueRepository.transition(queueId, QueueAction.StartDownload)

            // Kept across attempts on purpose: partial files are what makes a resumed download
            // possible, and wiping the directory on entry meant every retry restarted from zero.
            val tempDir = File(context.cacheDir, "download_$queueId").apply { mkdirs() }

            // Kept alongside the files so verification can enforce the fingerprint the source
            // advertised, and so the artifact row records what the source claimed.
            var sourceSignatureHint: String? = null
            var sourceArtifactHash: String? = null

            val downloadedFiles: List<File> = try {
                when (entity.source) {
                    ManagedSource.RUSTORE.name -> {
                        val storeApp = ruStoreSource.details(entity.packageName, includeReviews = false)
                        val artifacts = ruStoreSource.resolveArtifacts(storeApp)
                        sourceSignatureHint = storeApp.signatureHint
                        sourceArtifactHash = artifacts.firstOrNull()?.sourceHash
                        downloader.download(artifacts, tempDir) { progress ->
                            currentCoroutineContext().ensureActive()
                            val snapshot = queueRepository.getById(queueId)
                            if (snapshot != null) {
                                coordinator.showTransfer(
                                    item = snapshot,
                                    downloadedBytes = progress.downloadedBytes,
                                    totalBytes = progress.totalBytes,
                                    speed = progress.bytesPerSecond,
                                    eta = progress.etaSeconds
                                )
                                queueRepository.transition(
                                    queueId,
                                    QueueAction.DownloadProgress(progress.downloadedBytes, progress.totalBytes)
                                )
                            }
                        }
                    }
                    ManagedSource.GITHUB.name -> {
                        val repo = GitHubRepository(
                            owner = entity.githubRepositoryOwner ?: error("Missing GitHub owner"),
                            name = entity.githubRepositoryName ?: error("Missing GitHub repo name")
                        )
                        val releases = gitHubSource.releases(repo)
                        val assetPattern = GitHubCatalog.find(repo.displayName)?.assetPattern()
                        // A pinned release id can point at a tag whose APK was removed, and the
                        // newest tag is often a rolling nightly or a notes-only release, so both
                        // paths fall back to the newest release that actually has an installable
                        // asset instead of throwing.
                        val release = entity.githubReleaseId
                            ?.let { id -> releases.firstOrNull { it.id == id } }
                            ?.takeIf { GitHubReleasePolicy.apkAssets(it, assetPattern).isNotEmpty() }
                            ?: GitHubReleasePolicy.selectRelease(releases, assetPattern)
                            ?: error("No APK asset in GitHub release")
                        val asset = GitHubReleasePolicy.preferredAsset(
                            assets = GitHubReleasePolicy.apkAssets(release, assetPattern),
                            supportedAbis = Build.SUPPORTED_ABIS.toList()
                        ) ?: error("No APK asset in GitHub release")

                        val file = downloader.downloadGitHubApk(asset, tempDir) { progress ->
                            currentCoroutineContext().ensureActive()
                            val snapshot = queueRepository.getById(queueId)
                            if (snapshot != null) {
                                coordinator.showTransfer(
                                    item = snapshot,
                                    downloadedBytes = progress.downloadedBytes,
                                    totalBytes = progress.totalBytes,
                                    speed = progress.bytesPerSecond,
                                    eta = progress.etaSeconds
                                )
                                queueRepository.transition(
                                    queueId,
                                    QueueAction.DownloadProgress(progress.downloadedBytes, progress.totalBytes)
                                )
                            }
                        }
                        listOf(file)
                    }
                    else -> error("Unknown source: ${entity.source}")
                }
            } catch (t: Throwable) {
                tempDir.deleteRecursively()
                coordinator.cancelTransfer()
                throw t
            }

            try {
                queueRepository.transition(queueId, QueueAction.StartVerification)

                // A GitHub row is created from a release asset, whose file name says nothing about
                // the package inside. Asserting the placeholder would fail every GitHub install, so
                // the package name is adopted from the archive instead of asserted against.
                val expectedPackageName = entity.packageName.takeIf {
                    entity.source != ManagedSource.GITHUB.name || !GitHubInstallScheduler.isPlaceholder(it)
                }
                val installed = expectedPackageName
                    ?.let { name -> storeRepository.installedApps().firstOrNull { it.packageName == name } }
                val verification = SigningVerifier.verifyArtifacts(
                    packageManager = context.packageManager,
                    files = downloadedFiles,
                    installed = installed,
                    expectedPackageName = expectedPackageName,
                    expectedSourceDigest = sourceSignatureHint
                )
                if (!verification.isValid) {
                    // Surfaced to the user through the queue's typed error code, not this text.
                    throw dev.wystore.data.TransferFailure(
                        retryable = false,
                        code = dev.wystore.updates.model.QueueErrorCode.SIGNATURE,
                        detail = verification.error?.name
                    )
                }

                // The identity read during verification is the only record install time can
                // re-check against, so it has to be stored with the files, not discarded.
                queueRepository.saveVerifiedArtifacts(
                    queueId = queueId,
                    identity = verification.identity,
                    files = downloadedFiles,
                    sourceHash = sourceArtifactHash
                )
                queueRepository.transition(queueId, QueueAction.Verified)

                // Silent root install, when the user enabled it and root is actually granted.
                // The policy and the installer existed but had no production caller, so the
                // setting did nothing and every update still needed the Android dialog.
                if (installSilentlyIfEnabled(queueId, installed != null)) return@withContext

                // One entry for the whole set. A bulk update used to post a notification per app
                // plus a group summary, so twenty updates meant twenty-one notifications.
                coordinator.publishReady(queueRepository.readyToInstallSnapshots())

                // The verified files have been copied into durable storage by now, so the
                // working directory and any partial files in it are no longer needed.
                tempDir.deleteRecursively()

                // Each finished download adds to private storage, so this is where the retention
                // and quota settings are applied. Failing to prune must not fail the download.
                runCatching { queueRepository.cleanupWithSettings(storeRepository.settings()) }
            } finally {
                coordinator.cancelTransfer()
            }
        }

        /**
         * Installs a verified download without the Android dialog when the user turned silent root
         * install on and root is granted. Returns true when the install was handled here.
         *
         * The persisted copies are re-verified rather than reusing the plan from the temporary
         * download directory, both because that directory is about to be deleted and because the
         * bytes that get installed should be the bytes that were checked.
         */
        private suspend fun installSilentlyIfEnabled(queueId: String, isUpdate: Boolean): Boolean {
            val settings = storeRepository.settings()
            if (!settings.rootSilentInstallEnabled) return false
            val rootInstaller = RootInstaller()
            val mode = InstallModePolicy.choose(
                silentRootInstallEnabled = true,
                rootAvailable = rootInstaller.isAvailable()
            )
            if (mode != InstallMode.SILENT_ROOT) return false

            val entity = queueRepository.getEntityById(queueId) ?: return false
            val files = queueRepository.artifactFilesFor(queueId)
            if (files.isEmpty()) return false

            val installedApp = storeRepository.installedApps()
                .firstOrNull { it.packageName == entity.packageName }
            val recheck = SigningVerifier.verifyArtifacts(
                packageManager = context.packageManager,
                files = files,
                installed = installedApp,
                expectedPackageName = entity.packageName
            )
            val plan = recheck.plan ?: return false

            queueRepository.transitionIfIn(
                queueId,
                setOf(QueueState.READY_TO_INSTALL),
                QueueAction.RequestInstallConfirmation
            )
            queueRepository.transitionIfIn(
                queueId,
                setOf(QueueState.AWAITING_USER_CONFIRMATION),
                QueueAction.StartInstall
            )

            val result = rootInstaller.install(plan, update = isUpdate)
            return if (result.success) {
                queueRepository.reconcileInstallResult(queueId, success = true)
                invalidateInstalledApps()
                coordinator.publishReady(queueRepository.readyToInstallSnapshots())
                true
            } else {
                // Fall back to the normal confirmation flow rather than stranding the item.
                queueRepository.resetReadyToInstall(queueId)
                false
            }
        }
    }

    private fun workingDirectory(queueId: String) = File(applicationContext.cacheDir, "download_$queueId")

    companion object {
        const val KEY_QUEUE_ID = "queue_id"
        const val MAX_RUN_ATTEMPTS = 5
    }
}
