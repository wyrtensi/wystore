package dev.wystore.background

import android.app.job.JobScheduler
import android.content.Context
import android.os.Build
import androidx.work.WorkManager
import dev.wystore.data.StoreRepository
import dev.wystore.data.local.WyStoreDatabase
import dev.wystore.updates.InstallSessionWriter
import dev.wystore.updates.InstalledAppRegistrar
import dev.wystore.updates.QueueOrigin
import dev.wystore.updates.StuckInstallPolicy
import dev.wystore.updates.model.QueueErrorCode
import dev.wystore.updates.model.QueueItemSnapshot
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

        // Older builds kept every download of an app; the extra ones crashed the Updates tab.
        runCatching { repository.discardAllSuperseded() }

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

        releaseStuckInstalls(appContext, repository, rows)
        releaseAbandonedHandovers(appContext, repository)

        // With auto-download off a check only lists what it found, and opening the app must not
        // turn that into downloads. A row someone pressed a button for is started either way.
        val autoDownload = runCatching { StoreRepository(appContext).settings().autoDownloadUpdates }.getOrDefault(false)
        val requestedWaiting = rows.any { it.state == QueueState.AVAILABLE && QueueOrigin.isUserRequested(it.priority) }
        if (autoDownload || requestedWaiting) {
            runCatching { QueuePump.startNext(appContext) }
        }
    }

    /** See [QueueRecoveryPolicy.isAbandonedHandover]. */
    private suspend fun releaseAbandonedHandovers(context: Context, repository: QueueRepository) {
        val now = System.currentTimeMillis()
        runCatching { WyStoreDatabase.getInstance(context).updateQueueDao.getAll() }
            .getOrDefault(emptyList())
            .filter { row ->
                val state = runCatching { QueueState.valueOf(row.state) }.getOrNull() ?: return@filter false
                QueueRecoveryPolicy.isAbandonedHandover(state, row.updatedAt, now)
            }
            .forEach { row -> runCatching { repository.resetReadyToInstall(row.id) } }
    }

    /** Frees an install slot a dialog nobody answered left taken - see [StuckInstallPolicy]. */
    private suspend fun releaseStuckInstalls(
        context: Context,
        repository: QueueRepository,
        rows: List<QueueItemSnapshot>
    ) {
        val dao = WyStoreDatabase.getInstance(context).updateQueueDao
        val installer = context.packageManager.packageInstaller
        rows.filter { it.state == QueueState.INSTALLING }.forEach { row ->
            runCatching {
                val session = dao.getSession(row.id)
                val awaitingUserSince = session
                    ?.takeIf { it.state == InstallSessionWriter.SESSION_AWAITING_USER }
                    ?.updatedAt
                val installedVersion = runCatching {
                    context.packageManager.getPackageInfo(row.packageName, 0).longVersionCode
                }.getOrNull()
                val outcome = StuckInstallPolicy.decide(
                    expectedVersionCode = row.versionCode,
                    awaitingUserSince = awaitingUserSince,
                    now = System.currentTimeMillis(),
                    installedVersionCode = installedVersion
                )
                if (outcome == StuckInstallPolicy.Outcome.LEAVE) return@runCatching
                // Read again: the result receiver may have closed the row since the snapshot, and
                // its answer is better than this guess.
                val current = dao.getById(row.id) ?: return@runCatching
                if (current.state != QueueState.INSTALLING.name) return@runCatching
                when (outcome) {
                    StuckInstallPolicy.Outcome.SUCCEEDED -> {
                        runCatching { InstalledAppRegistrar.register(context, current) }
                        repository.reconcileInstallResult(row.id, success = true)
                    }
                    else -> {
                        // The session would otherwise go on waiting on Android's side; an answer
                        // given to it later would install behind a row that has moved on.
                        session?.let { runCatching { installer.abandonSession(it.sessionId) } }
                        // Back to waiting with its files, as a "not now" in the dialog would be.
                        repository.reconcileInstallResult(
                            row.id,
                            success = false,
                            errorCode = QueueErrorCode.INSTALL_CANCELED,
                            errorDetail = "Install confirmation was left unanswered"
                        )
                    }
                }
            }
        }
    }
}
