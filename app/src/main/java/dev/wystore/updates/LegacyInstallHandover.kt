package dev.wystore.updates

import android.content.Context
import android.content.Intent
import dev.wystore.background.QueuePump
import dev.wystore.data.EventLog
import dev.wystore.updates.model.QueueErrorCode
import dev.wystore.updates.model.QueueState

/** Implemented by the Activity that can start the system installer and receive its answer. */
interface SystemInstallerHost {
    fun launchSystemInstaller(intent: Intent)
}

/**
 * The install handed to the system installer and still waiting for its answer.
 *
 * Written to disk before the dialog opens: the answer can arrive in a recreated Activity, or in a
 * new process, and by then nothing in memory remembers which queue row it belongs to.
 */
class LegacyInstallHandover(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("wystore_installer", Context.MODE_PRIVATE)

    fun begin(queueId: String, at: Long) {
        prefs.edit().putString(KEY_QUEUE_ID, queueId).putLong(KEY_AT, at).commit()
    }

    /** The install waiting for an answer, taken so that exactly one answer is ever applied to it. */
    data class Pending(val queueId: String, val handedOverAt: Long)

    /**
     * Takes the pending handover off disk. Called on the main thread where the answer arrives, so
     * the result callback and the check on resume cannot both apply one.
     */
    fun take(): Pending? {
        val queueId = prefs.getString(KEY_QUEUE_ID, null) ?: return null
        val handedOverAt = prefs.getLong(KEY_AT, 0L)
        prefs.edit().remove(KEY_QUEUE_ID).remove(KEY_AT).commit()
        return Pending(queueId, handedOverAt)
    }

    /**
     * Applies the installer's answer to the row it was waiting for.
     *
     * Also used with no answer at all: an Activity result is delivered before onResume, so a
     * handover still on disk when the Activity resumes will never get one - the process was killed
     * behind the dialog. The device is then the only witness, which [LegacyInstallOutcome] asks
     * first anyway.
     */
    suspend fun finish(pending: Pending, resultCode: Int, data: Intent?) {
        val queueId = pending.queueId
        val handedOverAt = pending.handedOverAt

        val repository = QueueRepository.getInstance(appContext)
        val entity = repository.getEntityById(queueId) ?: return
        // Only the install this handover started. A row already settled elsewhere - by the package
        // broadcast, or cancelled from the queue - must not have a late answer written over it.
        if (entity.state != QueueState.INSTALLING.name) return
        val installed = runCatching {
            @Suppress("DEPRECATION")
            appContext.packageManager.getPackageInfo(entity.packageName, 0)
        }.getOrNull()
        val outcome = LegacyInstallOutcome.decide(
            resultCode = resultCode,
            installResult = data?.takeIf { it.hasExtra(EXTRA_INSTALL_RESULT) }
                ?.getIntExtra(EXTRA_INSTALL_RESULT, 0),
            targetVersionCode = entity.versionCode,
            installedVersionCode = installed?.longVersionCode,
            installedUpdatedAt = installed?.lastUpdateTime,
            handedOverAt = handedOverAt
        )
        when (outcome) {
            LegacyInstallOutcome.Installed -> {
                runCatching { InstalledAppRegistrar.register(appContext, entity) }
                    .onFailure { error ->
                        runCatching {
                            EventLog(appContext).record(
                                entity.packageName,
                                "REGISTER_FAILED",
                                error.message ?: error::class.java.simpleName
                            )
                        }
                    }
                repository.reconcileInstallResult(queueId, success = true)
            }
            LegacyInstallOutcome.Declined -> repository.reconcileInstallResult(
                id = queueId,
                success = false,
                errorCode = QueueErrorCode.INSTALL_CANCELED
            )
            is LegacyInstallOutcome.Failed -> repository.reconcileInstallResult(
                id = queueId,
                success = false,
                errorCode = QueueErrorCode.INSTALL_FAILED,
                errorDetail = LegacyInstallOutcome.describe(outcome.installResult)
            )
        }
        runCatching { QueuePump.startNext(appContext) }
    }

    private companion object {
        const val KEY_QUEUE_ID = "legacy_handover_queue_id"
        const val KEY_AT = "legacy_handover_at"
        // Intent.EXTRA_INSTALL_RESULT is hidden; this is its value.
        const val EXTRA_INSTALL_RESULT = "android.intent.extra.INSTALL_RESULT"
    }
}
