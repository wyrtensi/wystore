package dev.wystore.permissions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

data class PermissionSnapshot(
    val notificationsGranted: Boolean,
    val canInstallUnknownApps: Boolean,
    val batteryOptimizationsIgnored: Boolean
)

class PermissionRepository(private val context: Context) {
    private val appContext = context.applicationContext

    fun snapshot(): PermissionSnapshot {
        val notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            NotificationManagerCompat.from(appContext).areNotificationsEnabled()
        } else {
            true
        }

        val canInstallUnknownApps = appContext.packageManager.canRequestPackageInstalls()

        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val batteryOptimizationsIgnored =
            powerManager?.isIgnoringBatteryOptimizations(appContext.packageName) == true

        return PermissionSnapshot(
            notificationsGranted = notificationsGranted,
            canInstallUnknownApps = canInstallUnknownApps,
            batteryOptimizationsIgnored = batteryOptimizationsIgnored
        )
    }

    fun applicationDetailsIntent(packageName: String = appContext.packageName): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun unknownSourcesIntent(packageName: String = appContext.packageName): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun notificationSettingsIntent(packageName: String = appContext.packageName): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun channelSettingsIntent(channelId: String, packageName: String = appContext.packageName): Intent =
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /**
     * Android's own dialog for the exemption, with the list of every app as the fallback.
     *
     * The list is where this used to send people: an alphabetical roll of everything installed, to
     * find this app in and change a setting whose name is not the one on the button they pressed.
     * The direct request is one dialog with a yes and a no, and the system still decides - the app
     * only asks. A device that does not offer it falls back to the list rather than to nothing.
     */
    fun batteryOptimizationSettingsIntent(packageName: String = appContext.packageName): Intent {
        val request = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (request.resolveActivity(appContext.packageManager) != null) return request
        return Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
