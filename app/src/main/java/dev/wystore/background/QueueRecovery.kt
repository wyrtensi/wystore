package dev.wystore.background

import android.app.job.JobScheduler
import android.content.Context
import android.os.Build
import androidx.work.WorkManager
import dev.wystore.data.StoreRepository
import dev.wystore.updates.QueueOrigin
import dev.wystore.updates.QueueRecoveryPolicy
import dev.wystore.updates.QueueRepository
import dev.wystore.updates.model.QueueState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Picks the queue up again when the app is opened.
 *
 * Updates a background check found are downloaded by background work, which the system is free to
 * stop or never to run. Opening the app is the moment to notice: a row whose runner is gone goes
 * back in line with its bytes, and the queue is started on whatever is waiting, under the same
 * rules an unattended download follows - Wi-Fi only, charging, and the per-app auto-update switch.
 */
object QueueRecovery {

    suspend fun resume(context: Context) = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val repository = QueueRepository.getInstance(appContext)
        val workManager = WorkManager.getInstance(appContext)
        val jobs = if (Build.VERSION.SDK_INT >= 34) appContext.getSystemService(JobScheduler::class.java) else null

        val rows = runCatching { repository.snapshotAll() }.getOrDefault(emptyList())
        rows
            .filter { it.state in QueueRecoveryPolicy.INTERRUPTIBLE_STATES }
            .forEach { row ->
                val workScheduled = runCatching {
                    workManager.getWorkInfosForUniqueWork(TransferDispatcher.downloadWorkName(row.id)).get()
                        .any { !it.state.isFinished }
                }.getOrDefault(true)
                val jobScheduled = runCatching {
                    jobs?.getPendingJob(TransferDispatcher.jobIdFor(row.id)) != null
                }.getOrDefault(true)
                if (QueueRecoveryPolicy.isOrphaned(row.state, workScheduled, jobScheduled)) {
                    runCatching { repository.releaseInterrupted(row.id) }
                }
            }

        // With auto-download off a check only lists what it found, and opening the app must not
        // turn that into downloads. A row someone pressed a button for is started either way.
        val autoDownload = runCatching { StoreRepository(appContext).settings().autoDownloadUpdates }.getOrDefault(false)
        val requestedWaiting = rows.any { it.state == QueueState.AVAILABLE && QueueOrigin.isUserRequested(it.priority) }
        if (autoDownload || requestedWaiting) {
            runCatching { QueuePump.startNext(appContext) }
        }
    }
}
