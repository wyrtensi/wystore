package dev.wystore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferRateTest {

    private val mb = 1024L * 1024L

    @Test
    fun theFirstSampleKnowsNoSpeedYet() {
        val first = TransferRate.next(previous = null, downloadedBytes = 10 * mb, nowMillis = 1_000)
        assertEquals(0L, first.bytesPerSecond)
        assertNull(TransferRate.etaSeconds(first, totalBytes = 100 * mb))
    }

    @Test
    fun speedIsBytesOverTime() {
        // The queue row only carries byte counts; the speed and the time left were known to the
        // notification alone, so the app's own screens said "calculating" for the whole download.
        val first = TransferRate.next(null, 10 * mb, 1_000)
        val second = TransferRate.next(first, 20 * mb, 2_000)
        assertEquals(10 * mb, second.bytesPerSecond)
        assertEquals(8L, TransferRate.etaSeconds(second, totalBytes = 100 * mb))
    }

    @Test
    fun aBurstIsSmoothedRatherThanJumpedTo() {
        val steady = TransferRate.next(TransferRate.next(null, 0, 0), 10 * mb, 1_000)
        val burst = TransferRate.next(steady, 40 * mb, 2_000)
        assertTrue(burst.bytesPerSecond in (10 * mb + 1) until 30 * mb)
    }

    @Test
    fun samplesTooCloseTogetherKeepTheLastSpeed() {
        val first = TransferRate.next(TransferRate.next(null, 0, 0), 10 * mb, 1_000)
        val same = TransferRate.next(first, 10 * mb + 1, 1_050)
        assertEquals(first.bytesPerSecond, same.bytesPerSecond)
    }

    @Test
    fun aRestartedTransferStartsOver() {
        val running = TransferRate.next(TransferRate.next(null, 0, 0), 50 * mb, 1_000)
        val restarted = TransferRate.next(running, 1 * mb, 2_000)
        assertEquals(0L, restarted.bytesPerSecond)
    }
}
