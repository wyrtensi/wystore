package dev.wystore.background

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.wystore.data.StoreSettings
import dev.wystore.data.classifyThrowable
import java.util.concurrent.TimeUnit

object UpdateCheckPolicy {
    fun evaluateAppEligibility(
        isManualCheck: Boolean,
        targetPackageName: String?,
        appPackageName: String,
        autoCheckEnabledForApp: Boolean
    ): Boolean {
        if (targetPackageName != null) {
            return targetPackageName == appPackageName
        }
        if (isManualCheck) {
            return true
        }
        return autoCheckEnabledForApp
    }

    /**
     * Whether a check that just found an update should also fetch it.
     *
     * This used to be a root-only path: without root the install needs Android's confirmation
     * dialog anyway, so downloading ahead of time looked like filling storage with files that
     * could not be installed unattended. In practice it meant pressing "check" found an update
     * and then stood still, and the download only began after a second trip to another screen.
     * A downloaded update still asks before installing - it just asks with the work already done.
     */
    fun shouldEnqueueDownload(
        autoDownloadEnabled: Boolean,
        rootBackgroundDownloadsEnabled: Boolean,
        isRootAvailable: Boolean
    ): Boolean {
        return autoDownloadEnabled || (rootBackgroundDownloadsEnabled && isRootAvailable)
    }

    /**
     * The network a check may run on.
     *
     * A periodic check is unattended traffic and keeps the Wi-Fi-only setting. A manual one is a
     * button someone just pressed: it used to inherit the same rule, so on mobile data the job sat
     * with an unsatisfied CONNECTIVITY constraint and the screen said "queued" for as long as the
     * phone stayed off Wi-Fi. What it fetches is a few kilobytes of version numbers; the setting is
     * there to protect downloads, and downloads still honour it.
     */
    fun checkRequiresUnmeteredNetwork(
        isManualCheck: Boolean,
        wifiOnly: Boolean,
        allowMobileData: Boolean
    ): Boolean = !isManualCheck && wifiOnly && !allowMobileData

    /**
     * Whether the whole check is worth running again.
     *
     * Retrying used to follow from a single app: one package the source could not answer for sent
     * the entire check back through WorkManager's backoff, again and again, and an app the store
     * simply does not carry made that permanent. If anything at all was checked successfully, the
     * network and the source are evidently up, and running the same round again would fail on the
     * same app - the failures are counted and reported instead. Only a round where every attempt
     * failed for a reason that could pass looks like a check worth repeating.
     */
    fun shouldRetryCheck(attempted: Int, retryableFailures: Int): Boolean =
        attempted > 0 && retryableFailures == attempted

    /**
     * Whether a check may fetch what it just found for this app.
     *
     * "Auto-update" off is answered to the user in as many words: the check will not download or
     * install that app's updates. A check aimed at one package honoured it, but a check over the
     * whole library did not - it looked at every managed app, which is right, and then handed
     * everything it found to the downloader, which is not. An excluded app now gets its row in the
     * queue, so the update is still visible and can be started by hand, and nothing is fetched
     * behind the user's back.
     */
    fun mayDownloadAfterCheck(autoUpdateEnabledForApp: Boolean): Boolean = autoUpdateEnabledForApp

    fun shouldRetryWorker(error: Throwable): Boolean {
        return try {
            val failure = classifyThrowable(error)
            failure.retryable
        } catch (e: Exception) {
            false
        }
    }
}

object UpdateWorkScheduler {
    const val PERIODIC_CHECK_WORK = "wystore:updates:periodic"

    /**
     * One name for every manual check.
     *
     * It used to carry the package, so each target produced a different unique work name — and the
     * ViewModel, which can only observe a fixed name, observed a third one that nothing enqueued.
     * The progress card therefore never left "queued" and the check button, disabled while a check
     * is active, stayed dead until the app was restarted. One name also matches the UI: there is
     * one button and one progress card, so one manual check at a time.
     */
    const val MANUAL_CHECK_WORK = "wystore:updates:manual"

    /**
     * Schedules — or unschedules — the recurring check.
     *
     * [managedAppCount] decides whether the job exists at all: an install with nothing adopted was
     * still waking the device every interval to iterate an empty list.
     */
    fun schedulePeriodicCheck(context: Context, settings: StoreSettings, managedAppCount: Int = 1) {
        if (!BackgroundPolicy.shouldSchedulePeriodicWork(managedAppCount)) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_CHECK_WORK)
            return
        }

        val network = if (
            UpdateCheckPolicy.checkRequiresUnmeteredNetwork(
                isManualCheck = false,
                wifiOnly = settings.wifiOnly,
                allowMobileData = settings.allowMobileData
            )
        ) NetworkType.UNMETERED else NetworkType.CONNECTED
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(network)
            .setRequiresCharging(settings.requiresCharging)
            .setRequiresBatteryNotLow(true)
            .build()

        val interval = settings.updateIntervalHours.coerceAtLeast(1)
        // A flex window lets the system fold this into a wake-up it was going to make anyway
        // instead of starting a radio session of its own on a fixed schedule.
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
            interval, TimeUnit.HOURS,
            BackgroundPolicy.flexMinutesFor(interval), TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_CHECK_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun checkPackageNow(context: Context, settings: StoreSettings, packageName: String?) {
        val network = if (
            UpdateCheckPolicy.checkRequiresUnmeteredNetwork(
                isManualCheck = true,
                wifiOnly = settings.wifiOnly,
                allowMobileData = settings.allowMobileData
            )
        ) NetworkType.UNMETERED else NetworkType.CONNECTED
        val request = OneTimeWorkRequestBuilder<UpdateCheckWorker>()
            .setInputData(
                Data.Builder()
                    .putBoolean(UpdateCheckWorker.KEY_MANUAL_CHECK, true)
                    .putString(UpdateCheckWorker.KEY_PACKAGE, packageName)
                    .build()
            )
            .setConstraints(Constraints.Builder().setRequiredNetworkType(network).build())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            MANUAL_CHECK_WORK,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
