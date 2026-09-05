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

    fun enqueue(context: Context, packageName: String, label: String, settings: StoreSettings) {
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            val repository = QueueRepository.getInstance(appContext)
            val queueEntity = repository.enqueueAvailableUpdate(
                packageName = packageName,
                label = label,
                versionName = "",
                versionCode = 0,
                source = ManagedSource.RUSTORE,
                priority = 10
            )
            TransferDispatcher.dispatch(appContext, queueEntity.id)
        }
    }
}
