package dev.wystore.updates

import android.app.Activity
import android.content.Intent
import dev.wystore.data.PendingUpdate
import dev.wystore.data.local.WyStoreDatabase
import dev.wystore.updates.model.QueueAction
import dev.wystore.updates.model.QueueErrorCode
import dev.wystore.updates.model.QueueState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UserConfirmedInstaller(private val activity: Activity) {
    private val sessionWriter = InstallSessionWriter(activity)
    private val database = WyStoreDatabase.getInstance(activity)
    private val queueRepository = QueueRepository.getInstance(activity)

    /** Returns whether the install actually reached Android; see the [install] below. */
    suspend fun install(update: PendingUpdate): Boolean = withContext(Dispatchers.IO) {
        val readyEntity = database.updateQueueDao.getByPackage(update.packageName)
            .firstOrNull { it.state in INSTALLABLE_STATES }
            ?: throw IllegalStateException("The downloaded APK is no longer on disk")

        install(readyEntity.id)
    }

    /**
     * Drives the durable states the install actually passes through.
     *
     * The row used to stay in READY_TO_INSTALL for the whole handover to Android, so when the
     * PackageInstaller callback came back and tried to record success from INSTALLING, the reducer
     * rejected it as an illegal transition and the result was lost. Both steps are now persisted
     * before control leaves the process, and a failure to hand over rolls the row back so the item
     * stays installable instead of being stranded.
     *
     * Returns false when the install was put off rather than started - the single queue slot was
     * busy - so the caller can tell "Android is asking the user" from "nothing is going to happen
     * for this one yet". Waiting on the second forever is how a queue of installs stops dead.
     */
    suspend fun install(queueId: String): Boolean = withContext(Dispatchers.IO) {
        queueRepository.transitionIfIn(
            id = queueId,
            allowedFrom = setOf(QueueState.READY_TO_INSTALL),
            action = QueueAction.RequestInstallConfirmation
        )

        val prepared = try {
            sessionWriter.prepare(queueId)
        } catch (whole: WholeApkRequiredException) {
            // Split parts on a device that refuses sessions: fetched again as one APK, which then
            // installs by itself. Put off, not failed.
            rollbackToReady(queueId)
            FirmwareInstallFallback.refetchWhole(activity, queueId)
            return@withContext false
        } catch (error: Throwable) {
            rollbackToReady(queueId)
            throw error
        }

        // A download may still hold the single slot. The prepared session is dropped and the row
        // goes back to waiting, so the install happens when the queue reaches it, rather than
        // reporting a failure for standing in a queue.
        if (!queueRepository.transitionIfFree(
                id = queueId,
                allowedFrom = setOf(QueueState.AWAITING_USER_CONFIRMATION),
                action = QueueAction.StartInstall
            )
        ) {
            runCatching { sessionWriter.abandon(queueId) }
            rollbackToReady(queueId)
            return@withContext false
        }

        try {
            withContext(Dispatchers.Main) {
                when (prepared) {
                    is PreparedInstall.LegacySingleApk -> launchSystemInstaller(prepared)
                    is PreparedInstall.Session -> sessionWriter.commitSession(prepared)
                }
            }
        } catch (error: Throwable) {
            // Android never took the install, so nothing will ever call back for this item.
            sessionWriter.abandon(queueId)
            if (SessionInstallRejection.isFirmwareRefusal(error.message)) {
                // The same refusal the receiver handles, only raised at commit rather than reported.
                FirmwareInstallFallback.afterRefusal(activity, queueId, error.message)
                return@withContext false
            }
            queueRepository.reconcileInstallResult(
                id = queueId,
                success = false,
                errorCode = QueueErrorCode.INSTALL_FAILED,
                errorDetail = error.message
            )
            throw error
        }
        true
    }

    /**
     * Hands the APK to the system installer and, where the Activity can take the answer, asks for
     * one. Without an answer a closed dialog left the row INSTALLING until the next reboot.
     */
    private fun launchSystemInstaller(prepared: PreparedInstall.LegacySingleApk) {
        val host = activity as? SystemInstallerHost
        if (host == null) {
            activity.startActivity(prepared.intent)
            return
        }
        LegacyInstallHandover(activity).begin(prepared.queueId, System.currentTimeMillis())
        host.launchSystemInstaller(
            Intent(prepared.intent).apply {
                // A result is only delivered within the caller's task.
                removeFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(Intent.EXTRA_RETURN_RESULT, true)
            }
        )
    }

    private suspend fun rollbackToReady(queueId: String) {
        queueRepository.transitionIfIn(
            id = queueId,
            allowedFrom = setOf(QueueState.AWAITING_USER_CONFIRMATION),
            action = QueueAction.Cancel
        )
        queueRepository.resetReadyToInstall(queueId)
    }

    private companion object {
        /**
         * A retap after the Activity was recreated finds the row already moved on, so those states
         * count as installable too instead of reporting the download as gone.
         */
        val INSTALLABLE_STATES = setOf(
            QueueState.READY_TO_INSTALL.name,
            QueueState.AWAITING_USER_CONFIRMATION.name,
            QueueState.AWAITING_UNKNOWN_SOURCES_PERMISSION.name
        )
    }
}
