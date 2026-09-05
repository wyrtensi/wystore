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
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getActivity(context, 7002, intent, flags)
    }
}
