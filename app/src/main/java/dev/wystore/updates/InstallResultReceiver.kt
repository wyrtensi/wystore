package dev.wystore.updates

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import dev.wystore.background.QueuePump
import dev.wystore.data.EventLog
import dev.wystore.updates.model.QueueErrorCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class InstallResultReceiver : BroadcastReceiver() {

    private fun canConfirmNow(context: Context): Boolean = runCatching {
        val importance = ActivityManager.RunningAppProcessInfo().also {
            ActivityManager.getMyMemoryState(it)
        }.importance
        PendingUserActionPolicy.canConfirmNow(importance)
    }.getOrDefault(false)

    /** Hands the item back to the queue, and to the notification the user can act on. */
    private fun deferConfirmation(context: Context, queueId: String) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                returnForConfirmation(context, QueueRepository.getInstance(context), queueId)
            } catch (error: Throwable) {
                android.util.Log.w("InstallResultReceiver", "Could not defer install $queueId", error)
            } finally {
                // The slot is free again. The download that handed this row over already asked for
                // the next item while the row still held the slot, so nothing else would start it.
                runCatching { dev.wystore.background.QueuePump.startNext(context) }
                pendingResult.finish()
            }
        }
    }
    /**
     * Back to ready, with a standing request to install it: the app carries that out with Android's
     * dialog the next time it is on screen, and the notification offers it until then.
     */
    private suspend fun returnForConfirmation(context: Context, repository: QueueRepository, queueId: String) {
        repository.getEntityById(queueId)?.let { entity ->
            runCatching { AutoInstallStore(context).request(entity.packageName) }
        }
        repository.resetReadyToInstall(queueId)
        runCatching {
            dev.wystore.background.NotificationCoordinator(context)
                .publishReady(repository.readyToInstallSnapshots())
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != InstallSessionWriter.ACTION_INSTALL_RESULT) return

        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val queueId = intent.getStringExtra(InstallSessionWriter.EXTRA_QUEUE_ID) ?: return
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val silentRequested = intent.getBooleanExtra(InstallSessionWriter.EXTRA_SILENT, false)

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirmation = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (confirmation != null && canConfirmNow(context)) {
                context.startActivity(confirmation)
                // Written down so a dialog left unanswered can be told apart later - see
                // StuckInstallPolicy. Nothing else reports that the dialog went away.
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        runCatching {
                            dev.wystore.data.local.WyStoreDatabase.getInstance(context).updateQueueDao
                                .updateSessionState(queueId, InstallSessionWriter.SESSION_AWAITING_USER, System.currentTimeMillis())
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            } else {
                // Android refuses a background activity start without a word, so this row would
                // sit in INSTALLING forever with nothing ever coming back for it. Put it back
                // where the user can reach it and leave the standing request to install.
                deferConfirmation(context, queueId)
            }
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = QueueRepository.getInstance(context)
                // The row is not guaranteed to still be INSTALLING: the process may have died and
                // been restarted, or Android may redeliver the broadcast. Pushing the outcome
                // through the reducer would throw on an illegal transition and lose the result, so
                // the terminal state is reconciled instead.
                when (status) {
                    PackageInstaller.STATUS_SUCCESS -> {
                        // Registered before reconciling, while the row still carries the verified
                        // identity and source; without this the app installs and then never
                        // receives an update, because nothing marks it as managed.
                        repository.getEntityById(queueId)?.let { entity ->
                            runCatching { InstalledAppRegistrar.register(context, entity) }
                                .onFailure { error ->
                                    // The app is installed but nothing marks it as managed, so it
                                    // will never be offered an update again - and the install
                                    // looked like a complete success.
                                    runCatching {
                                        EventLog(context).record(
                                            packageName = entity.packageName,
                                            code = "REGISTER_FAILED",
                                            detail = error.message ?: error::class.java.simpleName
                                        )
                                    }
                                }
                        }
                        repository.reconcileInstallResult(queueId, success = true)
                    }
                    PackageInstaller.STATUS_FAILURE_ABORTED -> if (
                        SilentInstallRefusal.isRefusal(
                            status = status,
                            silentRequested = silentRequested,
                            sessionState = runCatching {
                                dev.wystore.data.local.WyStoreDatabase.getInstance(context).updateQueueDao
                                    .getSession(queueId)?.state
                            }.getOrNull()
                        )
                    ) {
                        // Nobody cancelled anything: the system refused to install without asking.
                        // From now on this firmware is asked with the dialog, starting with this one.
                        runCatching { SilentInstallRefusal(context).record() }
                        runCatching {
                            EventLog(context).record(
                                packageName = repository.getEntityById(queueId)?.packageName.orEmpty(),
                                code = "SILENT_INSTALL_REFUSED",
                                detail = message ?: "aborted"
                            )
                        }
                        returnForConfirmation(context, repository, queueId)
                    } else {
                        repository.reconcileInstallResult(
                            id = queueId,
                            success = false,
                            errorCode = QueueErrorCode.INSTALL_CANCELED,
                            errorDetail = message
                        )
                    }
                    // Not a failed install: the firmware refuses sessions, and the same APK goes
                    // through its own installer instead. Nothing is shown as an error.
                    else -> if (SessionInstallRejection.isFirmwareRefusal(message)) {
                        FirmwareInstallFallback.afterRefusal(context, queueId, message)
                    } else repository.reconcileInstallResult(
                        id = queueId,
                        success = false,
                        errorCode = QueueErrorCode.INSTALL_FAILED,
                        errorDetail = message ?: "PackageInstaller reported failure"
                    )
                }
            } catch (error: Throwable) {
                android.util.Log.w("InstallResultReceiver", "Could not record install result for $queueId", error)
            } finally {
                // The install slot is free either way, so whatever is queued behind can start.
                runCatching { QueuePump.startNext(context) }
                pendingResult.finish()
            }
        }
    }
}
