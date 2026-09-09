package dev.wystore.background

import android.content.Context
import android.os.PowerManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.wystore.data.EventLog
import dev.wystore.data.GitHubCatalog
import dev.wystore.data.GitHubInstallState
import dev.wystore.data.GitHubInstallStatePolicy
import dev.wystore.data.GitHubReleasePolicy
import dev.wystore.data.GitHubReleaseSource
import dev.wystore.data.InstallSource
import dev.wystore.data.InstalledApp
import dev.wystore.data.ManagedApp
import dev.wystore.data.ManagedSource
import dev.wystore.data.CheckProblemReason
import dev.wystore.data.RuStoreSource
import dev.wystore.data.SourceError
import dev.wystore.data.SourceFormatException
import dev.wystore.data.UpdateCheckProblem
import dev.wystore.data.SignatureCompatibility
import dev.wystore.data.SignatureCompatibilityPolicy
import dev.wystore.R
import dev.wystore.data.StoreRepository
import dev.wystore.data.StoreSettings
import dev.wystore.data.UpdateCheckSummary
import dev.wystore.selfupdate.SelfUpdateChecker
import dev.wystore.selfupdate.SelfUpdateStatus
import dev.wystore.root.RootInstaller
import dev.wystore.updates.QueueOrigin
import dev.wystore.updates.QueueRepository
import dev.wystore.updates.model.QueueState
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
                autoCheckEnabledForApp = managed.autoUpdate,
                showExcludedUpdates = settings.showExcludedUpdates
            )
        }

        // An app taken out of auto-updates leaves nothing behind either. A row queued before the
        // switch was turned off - or by an older build, which queued them all - would otherwise sit
        // in the queue for good, since nothing automatic will ever start it. A row the user asked
        // for by hand is theirs and is left alone.
        if (!settings.showExcludedUpdates) {
            for (managed in managedApps.filterNot { it.autoUpdate }) {
                runCatching {
                    queueRepository.snapshotAll()
                        .filter { it.packageName == managed.packageName }
                        .filter { it.state == QueueState.AVAILABLE }
                        .filterNot { QueueOrigin.isUserRequested(it.priority) }
                        .forEach { queueRepository.discard(it.id) }
                }
            }
        }

        var attempted = 0
        var retryableFailures = 0
        var updatesFound = 0
        var problems = 0
        // Named, not just counted: "2 problems" says neither which apps nor whether they are
        // unreachable for a moment or cannot be updated from here at all.
        val problemApps = mutableListOf<UpdateCheckProblem>()
        // Queue rows created by this run, so an unattended download starts only what was just
        // found rather than everything ever left in the queue. Carried with their package names:
        // a row id in the failure log is a UUID nobody can match to an app.
        val queuedThisRun = mutableListOf<QueuedRow>()

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

            if (managed.source == ManagedSource.GITHUB && !settings.githubEnabled) {
                continue
            }

            try {
                attempted++
                val queuedId = when (managed.source) {
                    ManagedSource.RUSTORE -> when (val outcome = checkRuStoreUpdate(managed, local)) {
                        is RuStoreCheck.Queued -> outcome.id
                        RuStoreCheck.UpToDate -> null
                        // Not a failure, and emphatically not "up to date": no update from this
                        // source can install over the app at all until it is reinstalled.
                        RuStoreCheck.SignatureChanged -> {
                            problems++
                            problemApps += UpdateCheckProblem(
                                packageName = managed.packageName,
                                label = managed.label.ifBlank { managed.packageName },
                                reason = CheckProblemReason.SIGNATURE_CHANGED
                            )
                            runCatching {
                                EventLog(applicationContext).record(
                                    packageName = managed.packageName,
                                    code = "SIGNATURE_CHANGED",
                                    detail = "Source signs with a different certificate"
                                )
                            }
                            null
                        }
                    }
                    ManagedSource.GITHUB -> checkGitHubUpdate(managed, local.versionName)
                    null -> null
                }
                if (queuedId != null) {
                    updatesFound++
                    // Found, and shown in the queue either way. Only an app the user still lets
                    // update by itself is handed to the downloader; see mayDownloadAfterCheck.
                    if (UpdateCheckPolicy.mayDownloadAfterCheck(managed.autoUpdate)) {
                        queuedThisRun += QueuedRow(id = queuedId, packageName = managed.packageName)
                    }
                }
            } catch (c: CancellationException) {
                throw c
            } catch (error: Throwable) {
                problems++
                problemApps += UpdateCheckProblem(
                    packageName = managed.packageName,
                    label = managed.label.ifBlank { managed.packageName },
                    // "Could not be reached" and "is not there" read the same in a count and mean
                    // opposite things: one is worth waiting out, the other never resolves.
                    reason = if (isMissingFromSource(error)) {
                        CheckProblemReason.NOT_IN_SOURCE
                    } else {
                        CheckProblemReason.UNREACHABLE
                    }
                )
                // The count alone tells nobody which app or why; the report needs both.
                runCatching {
                    EventLog(applicationContext).record(
                        packageName = managed.packageName,
                        code = "CHECK_FAILED",
                        detail = error.message ?: error::class.java.simpleName
                    )
                }
                if (UpdateCheckPolicy.shouldRetryWorker(error)) {
                    retryableFailures++
                }
            }
        }

        // Wy Store updates itself through the same queue as everything else, so this only has to
        // put the release in it; download, signature check and confirmation are unchanged.
        if (settings.selfUpdateEnabled && requestedPackage == null) {
            runCatching {
                val status = SelfUpdateChecker(applicationContext).check()
                if (status is SelfUpdateStatus.Available) {
                    queuedThisRun += QueuedRow(
                        id = SelfUpdateChecker(applicationContext).enqueue(status.release),
                        packageName = applicationContext.packageName
                    )
                    updatesFound++
                }
            }.onFailure { error ->
                // The store failing to check itself is worth the same words as any other app.
                problems++
                problemApps += UpdateCheckProblem(
                    packageName = applicationContext.packageName,
                    label = applicationContext.getString(R.string.app_name),
                    reason = CheckProblemReason.UNREACHABLE
                )
                runCatching {
                    EventLog(applicationContext).record(
                        packageName = applicationContext.packageName,
                        code = "SELF_UPDATE_CHECK_FAILED",
                        detail = error.message ?: error::class.java.simpleName
                    )
                }
            }
        }

        // Finding an update is only half of what the user asked for. Fetching it used to require
        // root, so on an ordinary phone the check announced the update and then stood still.
        if (queuedThisRun.isNotEmpty()) {
            startDownloads(settings, queuedThisRun)
        }

        // What was actually looked at. The candidate list is what the loop started with, and it
        // steps over apps as it goes - one no longer installed, a Google app the user has not
        // handed over, a GitHub app with GitHub switched off - so reporting its size counted apps
        // that were never checked.
        val detail = describe(attempted, updatesFound, problems)

        // The Updates screen has always had a "last check" card, and StoreRepository has always had
        // somewhere to put the result — but nothing ever wrote it, so the card never appeared.
        runCatching {
            repository.saveLastUpdateCheck(
                UpdateCheckSummary(
                    finishedAt = System.currentTimeMillis(),
                    detail = detail,
                    checked = attempted,
                    total = managedApps.size,
                    updates = updatesFound,
                    problems = problems,
                    manual = isManualCheck,
                    problemApps = problemApps.toList()
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

        return if (UpdateCheckPolicy.shouldRetryCheck(attempted, retryableFailures)) {
            Result.retry()
        } else {
            // Progress is dropped the moment a worker finishes, so the closing line has to travel
            // in the output data or the screen that started the check is told nothing.
            Result.success(
                UpdateCheckReport.result(
                    checked = attempted,
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

    /** What a RuStore check found. "Nothing to do" and "cannot be done" are different answers. */
    private sealed interface RuStoreCheck {
        data class Queued(val id: String) : RuStoreCheck
        data object UpToDate : RuStoreCheck
        data object SignatureChanged : RuStoreCheck
    }

    private suspend fun checkRuStoreUpdate(managed: ManagedApp, local: InstalledApp): RuStoreCheck {
        // Not caught here on purpose. Swallowing the failure made an unreachable source look like
        // an app that is already current: the run counted no problem, and the summary said
        // "everything up to date" while every request had failed. The loop above counts it and
        // decides whether the whole check is worth retrying.
        val app = ruStoreSource.details(managed.packageName, includeReviews = false)
        if (app.versionCode <= local.versionCode) return RuStoreCheck.UpToDate

        // The store states the certificate it signs with, and the phone knows the one it installed
        // under. When they are different, Android will not update in place whatever we download,
        // so an automatic check has no business fetching a hundred megabytes to find that out at
        // the end. The app's page says so instead, and a download the user starts by hand still
        // goes through - only the archive itself carries the signing lineage that could prove the
        // two certificates are the same developer after a key rotation.
        if (SignatureCompatibilityPolicy.evaluate(local.signingDigests, app.signatureHint) ==
            SignatureCompatibility.MISMATCH
        ) {
            // Reported rather than passed over in silence. There is a newer version and it cannot
            // be installed over this one; saying nothing made that look like an app with no update.
            return RuStoreCheck.SignatureChanged
        }
        return RuStoreCheck.Queued(
            queueRepository.enqueueAvailableUpdate(
                packageName = managed.packageName,
                label = app.name.ifBlank { managed.label },
                versionName = app.versionName,
                versionCode = app.versionCode,
                source = ManagedSource.RUSTORE
            ).id
        )
    }

    /** Returns the queue id when an update was queued for this app, or null when it is current. */
    private suspend fun checkGitHubUpdate(managed: ManagedApp, localVersionName: String): String? {
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

        // ...and the release id on its own is not enough either. It is absent for an app adopted
        // rather than installed here, and older installs recorded an asset id in its place, which
        // belongs to a different numbering entirely and can never match. Either way the check kept
        // offering a release the phone was already running, downloading it, and failing
        // verification on "not newer than installed" - reported to the user as a signature
        // problem. The tag says what the release is; that is what gets compared.
        // Only a release that is demonstrably newer. "Current" is the case above; "Unknown" means
        // one of the two version strings could not be read, and queueing on that guess is how the
        // loop started - the archive would be fetched and then refused for not being newer anyway.
        // The app's own page still offers a reinstall by hand when that is what someone wants.
        if (GitHubInstallStatePolicy.stateFor(localVersionName, latest.tagName) !=
            GitHubInstallState.UpdateAvailable
        ) {
            return null
        }

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
        queued: List<QueuedRow>
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
        queued.forEach { row ->
            runCatching { TransferDispatcher.dispatchUnattended(applicationContext, row.id, settings) }
                .onFailure { error ->
                    // The row is queued and nothing is coming for it. Silent, this looks exactly
                    // like an update that is simply taking its time.
                    runCatching {
                        EventLog(applicationContext).record(
                            packageName = row.packageName,
                            code = "DISPATCH_FAILED",
                            detail = error.message ?: error::class.java.simpleName
                        )
                    }
                }
        }
    }

    /** A row this run created, and the app it belongs to - which is what a report has to name. */
    private data class QueuedRow(val id: String, val packageName: String)

    /** Whether the source answered "no such app" rather than failing to answer at all. */
    private fun isMissingFromSource(error: Throwable): Boolean =
        error is SourceFormatException && error.error == SourceError.RUSTORE_NOT_FOUND

    companion object {
        const val KEY_MANUAL_CHECK = "manual_check"
        const val KEY_PACKAGE = "package_name"
    }
}
