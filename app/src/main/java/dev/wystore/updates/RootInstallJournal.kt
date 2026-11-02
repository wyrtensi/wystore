package dev.wystore.updates

import android.content.Context
import dev.wystore.data.local.InstallSessionEntity
import dev.wystore.data.local.WyStoreDatabase
import dev.wystore.updates.model.QueueState

/** Root has no app callback, so journal every boundary before a surviving shell can commit. */
class RootInstallJournal private constructor(private val context: Context, val queueId: String, private val token: String) {
    private val dao = WyStoreDatabase.getInstance(context).updateQueueDao

    suspend fun sessionCreated(sessionId: Int) = record(sessionId, PREPARED)
    suspend fun beforeCommit(sessionId: Int) = record(sessionId, COMMITTED)

    private suspend fun record(sessionId: Int, stage: String) = SourceSelectionGate.locked {
        val row = dao.getById(queueId) ?: error("Root install request was removed")
        val previous = dao.getSession(queueId) ?: error("Root installation claim was removed")
        check(previous.callbackToken == token && InstallAttemptTracker.isActive(queueId, token))
        check((stage == PREPARED && previous.state == CLAIMED) ||
            (stage == COMMITTED && previous.state == PREPARED && previous.sessionId == sessionId))
        check(row.state == QueueState.INSTALLING.name && !row.requiresUserConfirmation && !row.requiresUpdateOwnership)
        SourceSelectionCoordinator.requireCurrent(context, row)
        dao.insertOrReplaceSession(InstallSessionEntity(queueId, row.packageName, sessionId, token, stage))
    }

    fun close() { InstallAttemptTracker.end(queueId, token) }

    companion object {
        const val CLAIMED = "ROOT_CLAIMED"
        const val PREPARED = "ROOT_PREPARED"
        const val COMMITTED = "ROOT_COMMITTED"

        suspend fun claim(context: Context, queueId: String): RootInstallJournal? {
            val token = InstallAttemptTracker.begin(queueId) ?: return null
            try {
                if (QueueRepository.getInstance(context).claimRootInstall(queueId, token))
                    return RootInstallJournal(context.applicationContext, queueId, token)
                InstallAttemptTracker.end(queueId, token)
                return null
            } catch (error: Throwable) {
                InstallAttemptTracker.end(queueId, token)
                throw error
            }
        }
    }
}
