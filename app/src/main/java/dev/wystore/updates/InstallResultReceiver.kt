package dev.wystore.updates

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import dev.wystore.updates.model.QueueErrorCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != InstallSessionWriter.ACTION_INSTALL_RESULT) return

        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val queueId = intent.getStringExtra(InstallSessionWriter.EXTRA_QUEUE_ID) ?: return
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirmation = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (confirmation != null) {
                context.startActivity(confirmation)
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
                        }
                        repository.reconcileInstallResult(queueId, success = true)
                    }
                    PackageInstaller.STATUS_FAILURE_ABORTED ->
                        repository.reconcileInstallResult(
                            id = queueId,
                            success = false,
                            errorCode = QueueErrorCode.INSTALL_CANCELED,
                            errorDetail = message
                        )
                    else -> repository.reconcileInstallResult(
                        id = queueId,
                        success = false,
                        errorCode = QueueErrorCode.INSTALL_FAILED,
                        errorDetail = message ?: "Установка не удалась"
                    )
                }
            } catch (error: Throwable) {
                android.util.Log.w("InstallResultReceiver", "Could not record install result for $queueId", error)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
