package dev.wystore.background

import dev.wystore.data.DownloadProgress
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressNotificationLimiterTest {

    private fun progress(percent: Int, totalBytes: Long = 100_000_000L): DownloadProgress {
        val downloaded = (totalBytes * percent / 100).coerceAtMost(totalBytes)
        return DownloadProgress(
            downloadedBytes = downloaded,
            totalBytes = totalBytes,
            artifactIndex = 1,
            artifactCount = 1,
            bytesPerSecond = 1_000_000L,
            etaSeconds = 10L
        )
    }

    @Test
    fun throttlesProgressToMinimumIntervalUnlessTerminalOrChanged() {
        val limiter = ProgressNotificationLimiter(minIntervalMillis = 1000L)

        // First event (start) must always publish
        assertTrue(limiter.shouldPublish(progress(1), nowMillis = 0, terminal = false))

        // Within 250ms interval, should not publish
        assertFalse(limiter.shouldPublish(progress(1), nowMillis = 250, terminal = false))

        // After 1000ms and progress changed (1% -> 2%), must publish
        assertTrue(limiter.shouldPublish(progress(2), nowMillis = 1_000, terminal = false))

        // Even after 1500ms, if progress didn't change, should not publish
        assertFalse(limiter.shouldPublish(progress(2), nowMillis = 1_500, terminal = false))

        // Terminal event (100% complete) must always publish immediately
        assertTrue(limiter.shouldPublish(progress(100), nowMillis = 1_001, terminal = true))
    }

    @Test
    fun terminalEventsAlwaysBypassThrottle() {
        val limiter = ProgressNotificationLimiter(minIntervalMillis = 1000L)
        assertTrue(limiter.shouldPublish(progress(50), nowMillis = 100, terminal = false))
        // Immediate terminal 10ms later
        assertTrue(limiter.shouldPublish(progress(50), nowMillis = 110, terminal = true))
    }
}
