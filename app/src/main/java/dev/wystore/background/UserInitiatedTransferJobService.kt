package dev.wystore.background

import android.app.job.JobParameters
import android.app.job.JobService
import android.os.Build
import dev.wystore.data.classifyThrowable
import dev.wystore.updates.QueueRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Runs a queued transfer as an Android 14+ user-initiated data-transfer job.
 *
 * Two rules the platform enforces here: a UIDT job must publish a notification shortly after it
 * starts, or the system stops it; and each job must be independently cancellable, because one
 * service instance can be handed several jobs over its lifetime.
 */
class UserInitiatedTransferJobService : JobService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * One cancellable job per running transfer. The previous version cancelled a single
     * service-wide scope in [onStopJob], which killed the scope permanently: after the first
     * interrupted transfer every rescheduled job started and then did nothing, leaving its queue
     * item stuck in DOWNLOADING.
     */
    private val runningJobs = ConcurrentHashMap<Int, Job>()

    override fun onStartJob(params: JobParameters?): Boolean {
        val queueId = params?.extras?.getString(UpdateDownloadWorker.KEY_QUEUE_ID)
        if (queueId.isNullOrBlank()) return false

        val repository = QueueRepository.getInstance(applicationContext)
        val job = serviceScope.launch {
            try {
                val item = repository.getById(queueId)
                // Required by the platform for a user-initiated data transfer job.
                publishJobNotification(params, item?.label.orEmpty())

                UpdateDownloadWorker.TransferExecutor(applicationContext, repository).execute(queueId)
                jobFinished(params, false)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                val failure = classifyThrowable(error)
                if (failure.retryable) {
                    // The executor left the item mid-flight; park it so the rescheduled run can
                    // legally start a fresh download instead of throwing on its first transition.
                    runCatching { repository.resetForRetry(queueId, failure.code, failure.detail) }
                } else {
                    runCatching { repository.markFailed(queueId, failure.code, failure.detail) }
                }
                jobFinished(params, failure.retryable)
            } finally {
                runningJobs.remove(params.jobId)
            }
        }
        runningJobs[params.jobId] = job
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        val jobId = params?.jobId ?: return false
        runningJobs.remove(jobId)?.cancel()
        return true
    }

    private fun publishJobNotification(params: JobParameters, label: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        runCatching {
            val info = DownloadForegroundInfoFactory.createForegroundInfo(applicationContext, label)
            setNotification(
                params,
                info.notificationId,
                info.notification,
                JobService.JOB_END_NOTIFICATION_POLICY_REMOVE
            )
        }
    }
}
