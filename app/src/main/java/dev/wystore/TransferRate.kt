package dev.wystore

/**
 * Speed and time left for a download, worked out from the byte counts the queue row carries.
 *
 * The row stores how far a transfer has got and nothing else; the speed was known to the
 * notification alone, so every screen in the app said "calculating" for the whole download and the
 * queue card showed a bare bar. Successive rows are enough to tell: bytes gained over time passed,
 * smoothed so one quick burst does not swing the estimate.
 */
object TransferRate {

    data class Sample(val downloadedBytes: Long, val atMillis: Long, val bytesPerSecond: Long)

    /** Closer than this, two samples say more about scheduling than about the connection. */
    private const val MIN_INTERVAL_MILLIS = 250L

    /** Weight of the newest measurement against the running estimate. */
    private const val SMOOTHING = 0.3

    fun next(previous: Sample?, downloadedBytes: Long, nowMillis: Long): Sample {
        if (previous == null || downloadedBytes < previous.downloadedBytes) {
            return Sample(downloadedBytes, nowMillis, 0)
        }
        val elapsed = nowMillis - previous.atMillis
        if (elapsed < MIN_INTERVAL_MILLIS) return previous
        val measured = (downloadedBytes - previous.downloadedBytes) * 1000 / elapsed
        val rate = if (previous.bytesPerSecond <= 0) {
            measured
        } else {
            (SMOOTHING * measured + (1 - SMOOTHING) * previous.bytesPerSecond).toLong()
        }
        return Sample(downloadedBytes, nowMillis, rate)
    }

    fun etaSeconds(sample: Sample, totalBytes: Long): Long? {
        if (sample.bytesPerSecond <= 0 || totalBytes <= 0) return null
        val left = (totalBytes - sample.downloadedBytes).coerceAtLeast(0)
        return left / sample.bytesPerSecond
    }
}
