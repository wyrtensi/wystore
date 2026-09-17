package dev.wystore.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A 429 during an update check was read as "this app is not in RuStore", so a check that ran into
 * the source's rate limit reported a dozen apps as gone from the store.
 */
class RuStoreRateLimitPolicyTest {

    @Test
    fun `too many requests is its own error and never not found`() {
        assertEquals(SourceError.RUSTORE_RATE_LIMITED, RuStoreRateLimitPolicy.errorFor(429))
        assertEquals(SourceError.RUSTORE_NOT_FOUND, RuStoreRateLimitPolicy.errorFor(404))
        assertEquals(SourceError.RUSTORE_UNAVAILABLE, RuStoreRateLimitPolicy.errorFor(503))
    }

    @Test
    fun `a rate limit is retryable and not reported as a changed source`() {
        assertEquals(true, SourceError.RUSTORE_RATE_LIMITED.retryable)
        assertEquals(dev.wystore.updates.model.QueueErrorCode.RATE_LIMITED, SourceError.RUSTORE_RATE_LIMITED.queueCode)
    }

    @Test
    fun `waits twice then gives up`() {
        assertEquals(3_000L, RuStoreRateLimitPolicy.delayBeforeRetry(0, null))
        assertEquals(10_000L, RuStoreRateLimitPolicy.delayBeforeRetry(1, null))
        assertNull(RuStoreRateLimitPolicy.delayBeforeRetry(2, null))
    }

    @Test
    fun `a stated wait is honoured up to the cap and never shorter than the default`() {
        assertEquals(20_000L, RuStoreRateLimitPolicy.delayBeforeRetry(0, 20_000L))
        assertEquals(3_000L, RuStoreRateLimitPolicy.delayBeforeRetry(0, 500L))
        assertNull(RuStoreRateLimitPolicy.delayBeforeRetry(0, 120_000L))
    }
}
