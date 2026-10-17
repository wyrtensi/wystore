package dev.wystore.updates

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import dev.wystore.data.local.toSnapshot
import dev.wystore.updates.model.QueueState
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
                val packageInfo = dev.wystore.data.InstalledPackageLookup.packageInfo(packageManager, packageName) ?: return@launch

                val installedVersionCode = packageInfo.longVersionCode

                val dao = dev.wystore.data.local.WyStoreDatabase.getInstance(context).updateQueueDao
                val queueEntities = dao.getByPackage(packageName)
                // An agent installed this over adb from a row it was handed. The row waits at
                // READY_TO_INSTALL, which nothing below reconciles, so it is confirmed here - only
                // if what landed is the file that row verified.
                val handoff = dev.wystore.agent.AdbHandoff(context)
                queueEntities
                    .filter { handoff.isHandedOff(it.id) && it.state == QueueState.READY_TO_INSTALL.name }
                    .forEach { runCatching { handoff.confirm(it.id) } }
                // "The package is now on the device" is the one event that always fires, whichever
                // installer path ran and whether or not Wy Store was in the foreground. Registering
                // here is what makes an installed app eligible for later update checks; doing it
                // only from the install callback missed every race with this receiver.
                // Only a row that verified a file can vouch for what landed: every install Wy Store
                // makes comes after verification. The first row used to be taken, and a row still
                // downloading has no digests, so an app installed from elsewhere meanwhile was
                // adopted with its own signature pinned.
                queueEntities
                    .filter { it.signingDigests.isNotBlank() && it.versionCode <= installedVersionCode }
                    .maxByOrNull { it.versionCode }
                    ?.let { entity -> runCatching { InstalledAppRegistrar.register(context, entity) } }
                for (entity in queueEntities) {
                    val snapshot = entity.toSnapshot()
                    val reconcileAction = InstallReconciliationPolicy.reconcileObservedPackageChange(
                        item = snapshot,
                        newVersionCode = installedVersionCode
                    )
                    if (reconcileAction != null) {
                        // The install result receiver fires for the same install and may have
                        // closed the row first; the refused step must not kill the process.
                        runCatching { repository.transition(entity.id, reconcileAction) }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
