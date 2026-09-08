package dev.wystore.background

import android.content.Context
import android.os.PowerManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.wystore.data.GitHubCatalog
import dev.wystore.data.GitHubReleasePolicy
import dev.wystore.data.GitHubReleaseSource
import dev.wystore.data.InstallSource
import dev.wystore.data.ManagedApp
import dev.wystore.data.ManagedSource
import dev.wystore.data.RuStoreSource
import dev.wystore.R
import dev.wystore.data.StoreRepository
import dev.wystore.data.StoreSettings
import dev.wystore.data.UpdateCheckSummary
import dev.wystore.selfupdate.SelfUpdateChecker
import dev.wystore.selfupdate.SelfUpdateStatus
import dev.wystore.root.RootInstaller
import dev.wystore.updates.QueueRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class UpdateCheckWorker(
    appContext: Context,
    parameters: WorkerParameters
) : CoroutineWorker(appContext, parameters) {

    private val repository = StoreRepository(appContext)
    private val ruStoreSource = RuStoreSource.getInstance(appContext)
    private val gitHubSource = GitHubReleaseSource(appContext)
    private val queueRepository = QueueRepository.getInstance(appContext)

    override suspend fun doWork(): Result {
        val isManualCheck = inputData.getBoolean(KEY_MANUAL_CHECK, false)
        val requestedPackage = inputData.getString(KEY_PACKAGE) ?: inputData.getString("package")

        val installed = repository.installedApps().associateBy { it.packageName }
        val managedApps = repository.retainManagedInstalled(installed.keys)

        val settings = repository.settings()
        val powerSaveMode = runCatching {
            applicationContext.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
        }.getOrDefault(false)
        // Standing down here rather than in the constraints: WorkManager has no battery-saver
        // constraint, and a check skipped now simply happens at the next period.
        if (!BackgroundPolicy.shouldRunCheck(
                manual = isManualCheck,
                powerSaveMode = powerSaveMode,
                respectBatterySaver = settings.respectBatterySaver,
                managedAppCount = managedApps.size
            )
        ) {
            return Result.success()
        }

        val candidates = managedApps.filter { managed ->
            UpdateCheckPolicy.evaluateAppEligibility(
                isManualCheck = isManualCheck,
                targetPackageName = requestedPackage,
                appPackageName = managed.packageName,
                autoCheckEnabledForApp = managed.autoUpdate
            )
        }

        var anyRetryableFailure = false
        var updatesFound = 0
        var problems = 0
        // Queue rows created by this run, so an unattended download starts only what was just
        // found rather than everything ever left in the queue.
        val queuedThisRun = mutableListOf<String>()

        for ((index, managed) in candidates.withIndex()) {
            currentCoroutineContext().ensureActive()
            // Nothing was ever published here, so the card the Library shows while a check runs
            // had no counts to display and the check button's only feedback was going grey.
            runCatching {
                setProgress(
                    UpdateCheckReport.progress(
                        checked = index,
                        total = candidates.size,
                        updates = updatesFound,
                        detail = applicationContext.getString(
                            R.string.check_progress_detail, index, candidates.size
                        )
                    )
                )
            }
            val local = installed[managed.packageName] ?: continue
            if (local.source == InstallSource.GOOGLE_PLAY && !managed.forceWyStore && !isManualCheck) {
                continue
            }

            if (managed.source == ManagedSource.GITHUB && !repository.settings().githubEnabled) {
                continue
            }

            try {
                val queuedId = when (managed.source) {
                    ManagedSource.RUSTORE -> checkRuStoreUpdate(managed, local.versionCode)
                    ManagedSource.GITHUB -> checkGitHubUpdate(managed, local.versionCode)
                    null -> null
                }
                if (queuedId != null) {
                    updatesFound++
                    queuedThisRun += queuedId
                }
            } catch (c: CancellationException) {
                throw c
            } catch (error: Throwable) {
                problems++
                if (UpdateCheckPolicy.shouldRetryWorker(error)) {
                    anyRetryableFailure = true
                }
            }
        }

        // Wy Store updates itself through the same queue as everything else, so this only has to
        // put the release in it; download, signature check and confirmation are unchanged.
        if (settings.selfUpdateEnabled && requestedPackage == null) {
            runCatching {
                val status = SelfUpdateChecker(applicationContext).check()
                if (status is SelfUpdateStatus.Available) {
                    queuedThisRun += SelfUpdateChecker(applicationContext).enqueue(status.release)
                    updatesFound++
                }
            }
        }

        // Finding an update is only half of what the user asked for. Fetching it used to require
        // root, so on an ordinary phone the check announced the update and then stood still.
        if (queuedThisRun.isNotEmpty()) {
            startDownloads(settings, queuedThisRun)
        }

        val detail = describe(candidates.size, updatesFound, problems)

        // The Updates screen has always had a "last check" card, and StoreRepository has always had
        // somewhere to put the result — but nothing ever wrote it, so the card never appeared.
        runCatching {
            repository.saveLastUpdateCheck(
                UpdateCheckSummary(
                    finishedAt = System.currentTimeMillis(),
                    detail = detail,
                    checked = candidates.size,
                    total = managedApps.size,
                    updates = updatesFound,
                    problems = problems,
                    manual = isManualCheck
                )
            )
        }

        // The summary switch in Settings guarded a notification that was never written; a manual
        // check finished with no feedback at all unless it happened to find something.
        runCatching {
            NotificationCoordinator(applicationContext).showCheckSummary(
                manual = isManualCheck,
                updatesFound = updatesFound,
                problems = problems
            )
        }

        return if (anyRetryableFailure) {
            Result.retry()
        } else {
            // Progress is dropped the moment a worker finishes, so the closing line has to travel
            // in the output data or the screen that started the check is told nothing.
            Result.success(
                UpdateCheckReport.result(
                    checked = candidates.size,
                    total = managedApps.size,
                    updates = updatesFound,
                    detail = detail
                )
            )
        }
    }

    /** One line for the snackbar: what was looked at, what was found, what could not be reached. */
    private fun describe(checked: Int, updates: Int, problems: Int): String =
        when (UpdateCheckReport.outcome(updates, problems)) {
            CheckOutcome.UP_TO_DATE ->
                applicationContext.getString(R.string.check_result_up_to_date, checked)
            CheckOutcome.UPDATES_FOUND ->
                applicationContext.getString(R.string.check_result_updates, checked, updates)
            CheckOutcome.PROBLEMS ->
                applicationContext.getString(R.string.check_result_problems, checked, updates, problems)
        }

    /** Returns the queue id when an update was queued for this app, or null when it is current. */
    private suspend fun checkRuStoreUpdate(managed: ManagedApp, localVersionCode: Long): String? {
        val app = runCatching { ruStoreSource.details(managed.packageName, includeReviews = false) }
            .getOrNull() ?: return null
        if (app.versionCode <= localVersionCode) return null
        return queueRepository.enqueueAvailableUpdate(
            packageName = managed.packageName,
            label = app.name.ifBlank { managed.label },
            versionName = app.versionName,
            versionCode = app.versionCode,
            source = ManagedSource.RUSTORE
        ).id
    }

    /** Returns the queue id when an update was queued for this app, or null when it is current. */
    private suspend fun checkGitHubUpdate(managed: ManagedApp, localVersionCode: Long): String? {
        val repo = managed.githubRepository ?: return null
        val assetPattern = GitHubCatalog.find(repo.displayName)?.assetPattern()
        // Skips rolling nightly tags and releases with no installable APK; see GitHubReleasePolicy.
        val latest = GitHubReleasePolicy.selectRelease(gitHubSource.releases(repo), assetPattern)
            ?: return null

        // A GitHub release id is not a version code. Comparing the two — a nine-digit release id
        // against an app's versionCode — was always "newer", so every check re-offered an update
        // for an app that was already current. The installed release is tracked instead.
        val installedReleaseId = managed.githubReleaseId
        if (installedReleaseId != null && latest.id == installedReleaseId) return null

        return queueRepository.enqueueAvailableUpdate(
            packageName = managed.packageName,
            label = managed.label,
            versionName = latest.tagName.removePrefix("v"),
            // Placeholder only: verification replaces it with the version read out of the APK.
            versionCode = latest.id,
            source = ManagedSource.GITHUB,
            githubRepository = repo,
            githubReleaseId = latest.id
        ).id
    }

    /**
     * Fetches what was just found.
     *
     * These downloads keep the unattended constraints - Wi-Fi only and charging, if that is what
     * the settings say - even when a person started the check. Pressing "check" asks for version
     * numbers, not for a hundred megabytes over mobile data; a tap on Update or Download is the
     * explicit request, and that one still starts immediately through [TransferDispatcher.dispatch].
     *
     * Checking for root runs a shell command, so it only happens when auto-download is off and the
     * root path is the only thing that could still start a transfer.
     */
    private suspend fun startDownloads(
        settings: StoreSettings,
        queueIds: List<String>
    ) {
        val rootAvailable = if (settings.autoDownloadUpdates) {
            false
        } else {
            runCatching { RootInstaller().isAvailable() }.getOrDefault(false)
        }
        if (!UpdateCheckPolicy.shouldEnqueueDownload(
                autoDownloadEnabled = settings.autoDownloadUpdates,
                rootBackgroundDownloadsEnabled = settings.rootBackgroundDownloadsEnabled,
                isRootAvailable = rootAvailable
            )
        ) {
            return
        }
        queueIds.forEach { id ->
            runCatching { TransferDispatcher.dispatchUnattended(applicationContext, id, settings) }
        }
    }

    companion object {
        const val KEY_MANUAL_CHECK = "manual_check"
        const val KEY_PACKAGE = "package_name"
    }
}
