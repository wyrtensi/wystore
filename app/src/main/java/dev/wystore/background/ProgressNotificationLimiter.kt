package dev.wystore.background

import dev.wystore.data.DownloadProgress

class ProgressNotificationLimiter(
    val minIntervalMillis: Long = 1000L
) {
    private var lastPublishedTime: Long? = null
    private var lastPublishedPercent: Int? = null
    private var lastPublishedBytes: Long? = null

    fun shouldPublish(
        progress: DownloadProgress,
        nowMillis: Long,
        terminal: Boolean = false
    ): Boolean {
        if (terminal) {
            record(progress, nowMillis)
            return true
        }

        val lastTime = lastPublishedTime
        if (lastTime == null) {
            record(progress, nowMillis)
            return true
        }

        val elapsed = nowMillis - lastTime
        if (elapsed < minIntervalMillis) {
            return false
        }

        val currentPercent = (progress.fraction * 100).toInt()
        val percentChanged = lastPublishedPercent == null || currentPercent != lastPublishedPercent
        val bytesChanged = lastPublishedBytes == null || progress.downloadedBytes != lastPublishedBytes

        if (percentChanged || bytesChanged) {
            record(progress, nowMillis)
            return true
        }

        return false
    }

    private fun record(progress: DownloadProgress, nowMillis: Long) {
        lastPublishedTime = nowMillis
        lastPublishedPercent = (progress.fraction * 100).toInt()
        lastPublishedBytes = progress.downloadedBytes
    }
}
