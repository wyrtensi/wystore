package dev.wystore.background

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import dev.wystore.data.StoreSettings

object TransferDispatcher {
    enum class TransferMechanism {
        WORK_MANAGER_FOREGROUND,
        USER_INITIATED_JOB
    }

    fun downloadWorkName(queueId: String): String = "wystore:download:$queueId"

    fun determineMechanism(
        sdkInt: Int = Build.VERSION.SDK_INT,
        uidtSchedulingAllowed: Boolean = true
    ): TransferMechanism {
        return if (sdkInt >= 34 && uidtSchedulingAllowed) {
            TransferMechanism.USER_INITIATED_JOB
        } else {
            TransferMechanism.WORK_MANAGER_FOREGROUND
        }
    }

    fun jobIdFor(queueId: String): Int = 8000 + (queueId.hashCode() and 0x0fff)

    /**
     * Stops whichever mechanism is actually carrying this transfer. Cancelling only the queue row
     * leaves the worker running: it keeps writing progress onto a canceled item and can reschedule
     * itself, so both WorkManager and the JobScheduler entry have to be torn down here.
     */
    fun cancel(context: Context, queueId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(downloadWorkName(queueId))
        if (Build.VERSION.SDK_INT >= 34) {
            runCatching { context.getSystemService(JobScheduler::class.java)?.cancel(jobIdFor(queueId)) }
        }
    }

    /**
     * Starts a transfer the user is waiting for. Runs as soon as there is any connection, because
     * the user pressed a button and is watching.
     */
    fun dispatch(context: Context, queueId: String) {
        val mechanism = determineMechanism()
        if (mechanism == TransferMechanism.USER_INITIATED_JOB) {
            val scheduled = tryScheduleUserInitiatedJob(context, queueId)
            if (scheduled) return
        }
        enqueueWorkManagerForeground(context, queueId)
    }

    /**
     * Starts a transfer nobody asked for, right now.
     *
     * Unattended downloads have to obey the settings that a user-initiated one may ignore: an
     * automatic update must not spend mobile data when the user asked for Wi-Fi only, and it can
     * wait for the charger. It also never uses the user-initiated job mechanism, which is reserved
     * for transfers the user actually initiated.
     */
    fun dispatchUnattended(context: Context, queueId: String, settings: StoreSettings) {
        val network = if (settings.allowMobileData || !settings.wifiOnly) {
            NetworkType.CONNECTED
        } else {
            NetworkType.UNMETERED
        }
        enqueueWorkManagerForeground(
            context = context,
            queueId = queueId,
            constraints = Constraints.Builder()
                .setRequiredNetworkType(network)
                .setRequiresCharging(settings.requiresCharging)
                .setRequiresBatteryNotLow(true)
                .build(),
            expedited = false
        )
    }

    fun enqueueWorkManagerForeground(
        context: Context,
        queueId: String,
        constraints: Constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build(),
        expedited: Boolean = true
    ) {
        val request = OneTimeWorkRequestBuilder<UpdateDownloadWorker>()
            .setInputData(
                Data.Builder()
                    .putString(UpdateDownloadWorker.KEY_QUEUE_ID, queueId)
                    .build()
            )
            .setConstraints(constraints)
            .apply { if (expedited) setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST) }
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            downloadWorkName(queueId),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun tryScheduleUserInitiatedJob(context: Context, queueId: String): Boolean {
        if (Build.VERSION.SDK_INT < 34) return false
        return try {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return false
            val jobId = jobIdFor(queueId)
            val component = ComponentName(context, UserInitiatedTransferJobService::class.java)
            val extras = PersistableBundle().apply {
                putString(UpdateDownloadWorker.KEY_QUEUE_ID, queueId)
            }
            val jobInfo = JobInfo.Builder(jobId, component)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setUserInitiated(true)
                .setExtras(extras)
                .build()

            scheduler.schedule(jobInfo) == JobScheduler.RESULT_SUCCESS
        } catch (e: Exception) {
            false
        }
    }
}
