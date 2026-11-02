package dev.wystore.updates

import android.content.Context
import dev.wystore.data.EventLog
import dev.wystore.data.ManagedSource
import dev.wystore.data.SourceRequestPolicy
import dev.wystore.data.StoreRepository
import dev.wystore.data.local.WyStoreDatabase
import dev.wystore.root.RootInstaller
import dev.wystore.updates.model.QueueErrorCode
import dev.wystore.updates.model.QueueState
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A timeout is never evidence that an Android or root install stopped. Observe its session. */
object InstallSessionRecovery {
    suspend fun recoverAll(context: Context) {
        val dao = WyStoreDatabase.getInstance(context).updateQueueDao
        for (row in dao.getAll()) {
            try { recover(context, row.id) }
            catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { EventLog(context).record(row.packageName, "INSTALL_RECOVERY_FAILED", error.message) }
        }
    }

    suspend fun recover(context: Context, queueId: String): Boolean = withContext(Dispatchers.IO) {
        val dao = WyStoreDatabase.getInstance(context).updateQueueDao
        val session = dao.getSession(queueId) ?: return@withContext false
        val installer = context.packageManager.packageInstaller
        val info = if (session.sessionId >= 0) installer.getSessionInfo(session.sessionId) else null
        val decision = InstallRecoveryPolicy.decide(session.state,
            InstallAttemptTracker.isActive(queueId, session.callbackToken), info != null, info?.isSealed == true)
        if (decision == InstallRecoveryPolicy.Decision.LEAVE) return@withContext false
        if (decision == InstallRecoveryPolicy.Decision.ABANDON_THEN_OBSERVE) {
            if (session.state.startsWith("ROOT_")) {
                val result = RootInstaller().abandon(session.sessionId)
                check(result.success || installer.getSessionInfo(session.sessionId) == null) { result.output }
            } else installer.abandonSession(session.sessionId)
            // Android retains a session while its files are in use. Abandon returning is not proof.
            if (installer.getSessionInfo(session.sessionId) != null) return@withContext false
        }
        val row = dao.getById(queueId) ?: return@withContext false
        val repository = StoreRepository(context)
        val installed = repository.installedAppFresh(row.packageName)
        val completed = session.state in setOf(InstallSessionWriter.SESSION_COMMITTED,
            InstallSessionWriter.SESSION_AWAITING_USER, RootInstallJournal.COMMITTED) &&
            AwaitingConfirmationPolicy.wasInstalled(row.versionCode, installed?.versionCode,
                installed?.lastUpdateTime, session.updatedAt) &&
            installed?.signingDigests == row.signingDigests.split(',').filter(String::isNotBlank).toSet()
        if (completed) InstalledAppRegistrar.register(context, row)
        SourceSelectionGate.locked {
            if (dao.getSession(queueId) != session || InstallAttemptTracker.isActive(queueId, session.callbackToken)) return@locked false
            if (session.sessionId >= 0 && installer.getSessionInfo(session.sessionId) != null) return@locked false
            val current = dao.getById(queueId) ?: return@locked false
            val queue = QueueRepository.getInstance(context)
            val selected = repository.managedApps().firstOrNull { it.packageName == current.packageName }
            when {
                completed -> queue.reconcileInstallResult(queueId, success = true)
                current.state in setOf(QueueState.INSTALLED.name, QueueState.CANCELED.name, QueueState.SKIPPED.name) -> dao.deleteSession(queueId)
                !SourceRequestPolicy.isCurrent(selected, ManagedSource.valueOf(current.source), current.sourceRevision,
                    current.sourceReplacement, repository.sourceRevision(current.packageName)) -> {
                    dao.update(current.copy(state = QueueState.CANCELED.name, errorCode = QueueErrorCode.SOURCE_CHANGED.name))
                    dao.deleteSession(queueId)
                }
                dao.getArtifactsForQueue(queueId).any { File(it.path).isFile } -> queue.resetReadyToInstall(queueId)
                else -> {
                    queue.markFailed(queueId, QueueErrorCode.INTEGRITY, "Verified installation files are missing")
                    dao.deleteSession(queueId)
                }
            }
            true
        }
    }
}
