package dev.wystore.updates

import android.content.Context
import dev.wystore.background.TransferDispatcher
import dev.wystore.data.ManagedSource
import dev.wystore.data.StoreSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object ManualInstallScheduler {
    const val WORK_NAME = "wy_store_manual_install_queue"
    const val KEY_PACKAGE = "package_name"
    const val KEY_LABEL = "label"
    const val PACKAGE_TAG_PREFIX = "wy_store_package:"

    /**
     * Starts a transfer the user asked for.
     *
     * The row is prepared rather than blindly created: a package whose previous attempt stopped
     * still has that row in the database, and the download step is only legal from AVAILABLE. This
     * used to enqueue on top of a FAILED row, so every later attempt threw on its first transition
     * and the user was told it was a network error.
     */
    fun enqueue(context: Context, packageName: String, label: String, settings: StoreSettings) {
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            val repository = QueueRepository.getInstance(appContext)
            val queueEntity = repository.enqueueManualInstall(
                packageName = packageName,
                label = label,
                source = ManagedSource.RUSTORE
            ) ?: return@launch
            TransferDispatcher.dispatch(appContext, queueEntity.id)
        }
    }
}
