package dev.wystore.updates

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import dev.wystore.data.StoreRepository
import dev.wystore.data.local.WyStoreDatabase
import dev.wystore.data.local.toSnapshot
import dev.wystore.updates.model.QueueState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            UpdateScheduler.schedule(context, StoreRepository(context).settings())

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val repository = QueueRepository.getInstance(context)
                    val database = WyStoreDatabase.getInstance(context)
                    val installingItems = database.updateQueueDao.getAll()
                        .filter { it.state == QueueState.INSTALLING.name }

                    val pm = context.packageManager
                    for (entity in installingItems) {
                        val pkgInfo = runCatching {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                pm.getPackageInfo(entity.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
                            } else {
                                @Suppress("DEPRECATION")
                                pm.getPackageInfo(entity.packageName, 0)
                            }
                        }.getOrNull()

                        val installedVersion = pkgInfo?.longVersionCode

                        val action = InstallReconciliationPolicy.reconcileStaleInstalling(
                            item = entity.toSnapshot(),
                            installedVersionCode = installedVersion
                        )
                        repository.transition(entity.id, action)
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
