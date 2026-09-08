package dev.wystore.background

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferRetryPolicyTest {

    @Test
    fun aRetryableFailureIsRetriedWhileAttemptsRemain() {
        assertTrue(TransferRetryPolicy.shouldReschedule(retryable = true, attempts = 1))
        assertTrue(
            TransferRetryPolicy.shouldReschedule(
                retryable = true,
                attempts = TransferRetryPolicy.MAX_ATTEMPTS - 1
            )
        )
    }

    /**
     * The battery bug: without this the job asked to be run again for ever, and JobScheduler's
     * backoff turned that into a wake-up every few minutes all night.
     */
    @Test
    fun aRetryableFailureStopsAtTheCap() {
        assertFalse(
            TransferRetryPolicy.shouldReschedule(
                retryable = true,
                attempts = TransferRetryPolicy.MAX_ATTEMPTS
            )
        )
        assertFalse(
            TransferRetryPolicy.shouldReschedule(
                retryable = true,
                attempts = TransferRetryPolicy.MAX_ATTEMPTS + 3
            )
        )
    }

    @Test
    fun aPermanentFailureIsNeverRetried() {
        assertFalse(TransferRetryPolicy.shouldReschedule(retryable = false, attempts = 0))
    }

    @Test
    fun theTwoTransferPathsAgreeOnTheCap() {
        // WorkManager's path already stopped after five; the job path must not outlive it.
        assertEquals(UpdateDownloadWorker.MAX_RUN_ATTEMPTS, TransferRetryPolicy.MAX_ATTEMPTS)
    }

    private fun assertEquals(expected: Int, actual: Int) =
        org.junit.Assert.assertEquals(expected, actual)
}
