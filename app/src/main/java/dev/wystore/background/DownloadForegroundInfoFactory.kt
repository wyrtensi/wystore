package dev.wystore.background

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import dev.wystore.R
import dev.wystore.data.DownloadProgress

object DownloadForegroundInfoFactory {
    fun createForegroundInfo(
        context: Context,
        label: String,
        progress: DownloadProgress? = null
    ): ForegroundInfo {
        val percent = progress?.let { (it.fraction * 100).toInt().coerceIn(0, 100) }
        val contentText = if (progress != null && progress.totalBytes > 0) {
            val mbDownloaded = progress.downloadedBytes / 1_048_576.0
            val mbTotal = progress.totalBytes / 1_048_576.0
            context.getString(R.string.notif_downloading_progress, mbDownloaded, mbTotal, percent ?: 0)
        } else {
            context.getString(R.string.notif_downloading_indeterminate)
        }

        val notification = NotificationCompat.Builder(context, NotificationCoordinator.CHANNEL_TRANSFERS)
            .setSmallIcon(R.drawable.ic_stat_wystore)
            .setContentTitle(context.getString(R.string.notif_downloading_title, label))
            .setContentText(contentText)
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
