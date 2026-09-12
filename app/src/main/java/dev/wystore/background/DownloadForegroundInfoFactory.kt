package dev.wystore.background

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.ForegroundInfo
import dev.wystore.R
import dev.wystore.data.DownloadProgress

object DownloadForegroundInfoFactory {
    /**
     * @param label the app being fetched, or null when the notification has to exist before the
     * queue row behind it has been read - WorkManager asks for one before the worker starts.
     */
    fun createForegroundInfo(
        context: Context,
        label: String?,
        progress: DownloadProgress? = null,
        packageName: String? = null
    ): ForegroundInfo {
        val percent = progress?.let { (it.fraction * 100).toInt().coerceIn(0, 100) }
        val contentText = if (progress != null && progress.totalBytes > 0) {
            val mbDownloaded = progress.downloadedBytes / 1_048_576.0
            val mbTotal = progress.totalBytes / 1_048_576.0
            context.getString(R.string.notif_downloading_progress, mbDownloaded, mbTotal, percent ?: 0)
        } else {
            context.getString(R.string.notif_downloading_indeterminate)
        }

        // The channel has to exist before the notification is handed to anyone. Android 15
        // validates it when a user-initiated job publishes one and kills the process if it is
        // missing - and the throw arrives asynchronously, on the binder callback, where no
        // try/catch around this code can catch it. It is missing whenever nothing has built a
        // NotificationCoordinator in this process yet, which is exactly the case when a job or a
        // worker starts one cold.
        NotificationCoordinator.ensureChannels(context)

        val notification = NotificationCompat.Builder(context, NotificationCoordinator.CHANNEL_TRANSFERS)
            .setSmallIcon(R.drawable.ic_stat_wystore)
            // The same face as the progress notification this one is replaced by, so the entry does
            // not change appearance the moment the first bytes arrive.
            .setLargeIcon(packageName?.let { NotificationArt.iconFor(context, it) })
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setContentTitle(
                if (label != null) {
                    context.getString(R.string.notif_downloading_title, label)
                } else {
                    context.getString(R.string.notif_downloading_title_generic)
                }
            )
            .setContentText(contentText)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(100, percent ?: 0, progress == null || progress.totalBytes <= 0)
            .setOngoing(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NotificationCoordinator.NOTIFICATION_ID_TRANSFER,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NotificationCoordinator.NOTIFICATION_ID_TRANSFER, notification)
        }
    }
}
