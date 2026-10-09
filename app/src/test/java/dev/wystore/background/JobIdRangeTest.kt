package dev.wystore.background

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class JobIdRangeTest {

    @Test
    fun queueRowsRarelyShareAJobId() {
        // 4096 ids meant two rows of a few dozen collided often enough to cancel each other.
        val ids = (1..5_000).map { TransferDispatcher.jobIdFor(UUID.randomUUID().toString()) }.toSet()
        assertTrue(ids.size >= 4_995)
    }

    @Test
    fun idsStayPositiveAndAboveTheBase() {
        repeat(10_000) {
            val id = TransferDispatcher.jobIdFor(UUID.randomUUID().toString())
            assertTrue(id >= TransferDispatcher.TRANSFER_JOB_ID_BASE)
        }
    }
}
