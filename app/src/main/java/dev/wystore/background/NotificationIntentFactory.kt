package dev.wystore.background

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dev.wystore.MainActivity

object NotificationIntentFactory {
    const val EXTRA_DESTINATION = "extra_destination"
    const val EXTRA_PACKAGE_NAME = "extra_package_name"
    const val EXTRA_QUEUE_ID = "extra_queue_id"

    /** Set by the notification's own button: open the app and start the install, do not just show it. */
    const val EXTRA_START_INSTALL = "extra_start_install"

    fun readyDestination(packageName: String): String = "updates/$packageName"

    fun summaryDestination(): String = "updates"

    fun createReadyPackageIntent(
        context: Context,
        packageName: String,
        queueId: String? = null
    ): Intent = Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        putExtra(EXTRA_DESTINATION, readyDestination(packageName))
        putExtra(EXTRA_PACKAGE_NAME, packageName)
        if (queueId != null) putExtra(EXTRA_QUEUE_ID, queueId)
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    fun createReadyPackagePendingIntent(
        context: Context,
        packageName: String,
        queueId: String? = null,
        requestCode: Int = packageName.hashCode()
    ): PendingIntent {
        val intent = createReadyPackageIntent(context, packageName, queueId)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getActivity(context, requestCode, intent, flags)
    }

    fun createReadySummaryIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(EXTRA_DESTINATION, summaryDestination())
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

    fun createReadySummaryPendingIntent(context: Context): PendingIntent {
        val intent = createReadySummaryIntent(context)
        return activity(context, 7002, intent)
    }

    /**
     * The "Install" button on a ready notification.
     *
     * Tapping the notification itself opens the app's page, which is the right thing when the user
     * wants to look first. The button is for when they do not: it goes straight to the system
     * confirmation dialog for the APK that is already on disk.
     */
    fun createInstallPendingIntent(context: Context, packageName: String): PendingIntent {
        val intent = createReadyPackageIntent(context, packageName).apply {
            putExtra(EXTRA_START_INSTALL, true)
        }
        return activity(context, packageName.hashCode() xor INSTALL_REQUEST_SALT, intent)
    }

    /** The same, for the summary entry: install everything that is waiting, one after another. */
    fun createInstallAllPendingIntent(context: Context): PendingIntent {
        val intent = createReadySummaryIntent(context).apply { putExtra(EXTRA_START_INSTALL, true) }
        return activity(context, 7005, intent)
    }

    private fun activity(context: Context, requestCode: Int, intent: Intent): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getActivity(context, requestCode, intent, flags)
    }

    /**
     * Keeps the install button's request code clear of the one the notification body already uses
     * for the same package; sharing it would have the second PendingIntent overwrite the first.
     */
    private const val INSTALL_REQUEST_SALT = 0x1157A11
}
