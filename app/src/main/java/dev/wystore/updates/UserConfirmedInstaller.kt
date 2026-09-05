package dev.wystore.updates

import android.app.Activity
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

    suspend fun install(update: PendingUpdate) = withContext(Dispatchers.IO) {
        val readyEntity = database.updateQueueDao.getByPackage(update.packageName)
            .firstOrNull { it.state in INSTALLABLE_STATES }
            ?: throw IllegalStateException("Скачанный APK больше недоступен")

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
     */
    suspend fun install(queueId: String) = withContext(Dispatchers.IO) {
        queueRepository.transitionIfIn(
            id = queueId,
            allowedFrom = setOf(QueueState.READY_TO_INSTALL),
            action = QueueAction.RequestInstallConfirmation
        )

        val prepared = try {
            sessionWriter.prepare(queueId)
        } catch (error: Throwable) {
            rollbackToReady(queueId)
            throw error
        }

        queueRepository.transitionIfIn(
            id = queueId,
            allowedFrom = setOf(QueueState.AWAITING_USER_CONFIRMATION),
            action = QueueAction.StartInstall
        )

        try {
            withContext(Dispatchers.Main) {
                when (prepared) {
                    is PreparedInstall.LegacySingleApk -> activity.startActivity(prepared.intent)
                    is PreparedInstall.Session -> sessionWriter.commitSession(prepared)
                }
            }
        } catch (error: Throwable) {
            // Android never took the install, so nothing will ever call back for this item.
            sessionWriter.abandon(queueId)
            queueRepository.reconcileInstallResult(
                id = queueId,
                success = false,
                errorCode = QueueErrorCode.INSTALL_FAILED,
                errorDetail = error.message
            )
            throw error
        }
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
