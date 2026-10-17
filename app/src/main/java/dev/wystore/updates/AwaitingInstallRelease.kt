package dev.wystore.updates

import android.content.Context
import dev.wystore.background.NotificationCoordinator
import dev.wystore.background.QueuePump
import dev.wystore.data.EventLog
import dev.wystore.data.local.WyStoreDatabase
import dev.wystore.updates.model.QueueErrorCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Calls off an install that is waiting on Android's dialog, as "Cancel" in the dialog would have:
 * the session is dropped and the row goes back to waiting with its files.
 *
 * Used by the Cancel button on a waiting row and by [dev.wystore.background.QueueRecovery] once a
 * dialog has gone unanswered for its grace. Cancel used to skip installs altogether, so a dialog
 * closed by a stray tap left the row, and every install queued behind it, waiting for that minute.
 */
object AwaitingInstallRelease {

    /** Returns false, touching nothing, when the row is not waiting on the dialog. */
    suspend fun release(context: Context, queueId: String, reason: String): Boolean = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val dao = WyStoreDatabase.getInstance(appContext).updateQueueDao
        val row = dao.getById(queueId) ?: return@withContext false
        val session = dao.getSession(queueId)
        if (session == null || !AwaitingConfirmationPolicy.isAwaiting(row.state, session.state)) {
            return@withContext false
        }
        val repository = QueueRepository.getInstance(appContext)
        // The session stays "waiting for the user" until Android reports, so an Install pressed a
        // moment ago looks the same as a dialog nobody answered. The phone says which it was.
        val installed = dev.wystore.data.InstalledPackageLookup.packageInfo(appContext.packageManager, row.packageName)
        if (AwaitingConfirmationPolicy.wasInstalled(
                expectedVersionCode = row.versionCode,
                installedVersionCode = installed?.longVersionCode,
                installedUpdatedAt = installed?.lastUpdateTime,
                askedAt = session.updatedAt
            )
        ) {
            runCatching { InstalledAppRegistrar.register(appContext, row) }
                .onFailure { error ->
                    runCatching {
                        EventLog(appContext).record(row.packageName, "REGISTER_FAILED", error.message ?: error::class.java.simpleName)
                    }
                }
            repository.reconcileInstallResult(queueId, success = true)
        } else {
            // Recorded first: Android may report the dropped session as aborted at any moment, and
            // by then the user may have asked for the install again.
            ConfirmationDialogs.calledOff(session.sessionId)
            // The session would otherwise go on waiting on Android's side; an answer given to it
            // later would install behind a row that has moved on.
            runCatching { appContext.packageManager.packageInstaller.abandonSession(session.sessionId) }
            repository.reconcileInstallResult(
                id = queueId,
                success = false,
                errorCode = QueueErrorCode.INSTALL_CANCELED,
                errorDetail = reason
            )
        }
        runCatching { NotificationCoordinator(appContext).publishReady(repository.readyToInstallSnapshots()) }
        runCatching { QueuePump.startNext(appContext) }
        true
    }
}
