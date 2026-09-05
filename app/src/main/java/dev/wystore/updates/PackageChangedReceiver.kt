package dev.wystore.updates

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import dev.wystore.data.local.toSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PackageChangedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_PACKAGE_ADDED && action != Intent.ACTION_PACKAGE_REPLACED) return

        val packageName = intent.data?.schemeSpecificPart ?: return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = QueueRepository.getInstance(context)
                val packageManager = context.packageManager
                val packageInfo = runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
                    } else {
                        @Suppress("DEPRECATION")
                        packageManager.getPackageInfo(packageName, 0)
                    }
                }.getOrNull() ?: return@launch

                val installedVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode.toLong()
                }

                val dao = dev.wystore.data.local.WyStoreDatabase.getInstance(context).updateQueueDao
                val queueEntities = dao.getByPackage(packageName)
                // "The package is now on the device" is the one event that always fires, whichever
                // installer path ran and whether or not Wy Store was in the foreground. Registering
                // here is what makes an installed app eligible for later update checks; doing it
                // only from the install callback missed every race with this receiver.
                queueEntities.firstOrNull()?.let { entity ->
                    runCatching { InstalledAppRegistrar.register(context, entity) }
                }
                for (entity in queueEntities) {
                    val snapshot = entity.toSnapshot()
                    val reconcileAction = InstallReconciliationPolicy.reconcileObservedPackageChange(
                        item = snapshot,
                        newVersionCode = installedVersionCode
                    )
                    if (reconcileAction != null) {
                        repository.transition(entity.id, reconcileAction)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
