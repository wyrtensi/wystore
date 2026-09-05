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

    fun shouldEnqueueDownload(
        rootBackgroundDownloadsEnabled: Boolean,
        isRootAvailable: Boolean
    ): Boolean {
        return rootBackgroundDownloadsEnabled && isRootAvailable
    }

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

    fun manualCheckWork(packageName: String?): String =
        "wystore:updates:manual:${packageName ?: "all"}"

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

        val network = if (settings.allowMobileData || !settings.wifiOnly) NetworkType.CONNECTED else NetworkType.UNMETERED
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

    fun checkAllNow(context: Context, settings: StoreSettings) {
        checkPackageNow(context, settings, null)
    }

    fun checkPackageNow(context: Context, settings: StoreSettings, packageName: String?) {
        val network = if (settings.allowMobileData || !settings.wifiOnly) NetworkType.CONNECTED else NetworkType.UNMETERED
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
            manualCheckWork(packageName),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
