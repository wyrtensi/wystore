package dev.wystore.agent

import android.content.Context
import dev.wystore.data.StoreRepository
import dev.wystore.data.invalidateInstalledApps
import dev.wystore.data.local.WyStoreDatabase
import dev.wystore.updates.InstalledAppRegistrar
import dev.wystore.updates.QueueRepository
import dev.wystore.updates.model.QueueState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Queue rows whose install is handed to an agent over adb.
 *
 * The worker fetches and verifies such a row like any other and then stops at READY_TO_INSTALL
 * instead of installing it; the agent installs the verified files with Wy Store named as the
 * installer and reports back through [confirm]. A set of ids in preferences is enough to carry
 * that, and needs no change to the queue's schema.
 */
class AdbHandoff(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun ids(): Set<String> = prefs.getStringSet(KEY_IDS, emptySet()).orEmpty().toSet()

    fun isHandedOff(queueId: String): Boolean = queueId in ids()

    fun mark(queueId: String) = synchronized(LOCK) {
        prefs.edit().putStringSet(KEY_IDS, ids() + queueId).commit()
    }

    fun unmark(queueId: String) = synchronized(LOCK) {
        prefs.edit().putStringSet(KEY_IDS, ids() - queueId).commit()
    }

    /** The queue row of [packageName] an agent holds, if any. */
    suspend fun rowFor(packageName: String): String? = withContext(Dispatchers.IO) {
        val marked = ids()
        if (marked.isEmpty()) return@withContext null
        WyStoreDatabase.getInstance(appContext).updateQueueDao.getByPackage(packageName)
            .firstOrNull { it.id in marked }?.id
    }

    sealed interface Confirmation {
        data object Confirmed : Confirmation
        data class Refused(val reason: String) : Confirmation
    }

    /**
     * Adopts an install the agent made, when it is the file this row verified.
     *
     * It ends the way every other install path ends: the app is registered as one Wy Store
     * manages - its signature pinned, its later updates offered here - and the row is closed, which
     * also removes the stored copy.
     */
    suspend fun confirm(queueId: String): Confirmation = withContext(Dispatchers.IO) {
        val dao = WyStoreDatabase.getInstance(appContext).updateQueueDao
        val entity = dao.getById(queueId) ?: return@withContext Confirmation.Refused("unknown queue id")
        if (entity.state == QueueState.INSTALLED.name) {
            unmark(queueId)
            return@withContext Confirmation.Confirmed
        }
        // Only a row handed to the agent and waiting with verified files. Any other row's version
        // and digests describe an earlier attempt, and adopting the install on them would close a
        // download that is still running.
        if (!isHandedOff(queueId)) return@withContext Confirmation.Refused("not handed off")
        if (entity.state != QueueState.READY_TO_INSTALL.name) {
            return@withContext Confirmation.Refused("row is ${entity.state}, not READY_TO_INSTALL")
        }
        invalidateInstalledApps()
        val installed = StoreRepository(appContext).installedApp(entity.packageName)
        val verified = entity.signingDigests.split(",").filter { it.isNotBlank() }.toSet()
        if (!AgentPolicy.confirmsInstall(entity.versionCode, verified, installed?.versionCode, installed?.signingDigests.orEmpty())) {
            return@withContext Confirmation.Refused(
                "installed ${installed?.versionCode ?: "nothing"}, expected ${entity.versionCode} with the verified signature"
            )
        }
        runCatching { InstalledAppRegistrar.register(appContext, entity) }
        QueueRepository.getInstance(appContext).reconcileInstallResult(queueId, success = true)
        unmark(queueId)
        Confirmation.Confirmed
    }

    private companion object {
        const val PREFS = "adb_handoff"
        const val KEY_IDS = "queue_ids"
        val LOCK = Any()
    }
}
