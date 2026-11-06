package dev.wystore.updates

import dev.wystore.updates.model.QueueErrorCode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceFailurePolicyTest {

    /** A malformed answer or an explicit rate limit comes from the source. */
    @Test
    fun `the source answering badly is the source's problem`() {
        assertTrue(SourceFailurePolicy.looksLikeSourceProblem(QueueErrorCode.SOURCE_CHANGED))
        assertTrue(SourceFailurePolicy.looksLikeSourceProblem(QueueErrorCode.RATE_LIMITED))
    }

    /**
     * A dropped connection is the commonest failure there is and almost never the source. Offering
     * to update the store over it would cry wolf often enough that the real alarm gets ignored.
     */
    @Test
    fun `a lost connection is not`() {
        assertFalse(SourceFailurePolicy.looksLikeSourceProblem(QueueErrorCode.NETWORK))
        assertFalse(SourceFailurePolicy.looksLikeSourceProblem(QueueErrorCode.NETWORK_TIMEOUT))
    }

    @Test
    fun `neither is anything decided on this phone`() {
        assertFalse(SourceFailurePolicy.looksLikeSourceProblem(QueueErrorCode.STORAGE_FULL))
        assertFalse(SourceFailurePolicy.looksLikeSourceProblem(QueueErrorCode.SIGNATURE))
        assertFalse(SourceFailurePolicy.looksLikeSourceProblem(QueueErrorCode.INSTALL_CANCELED))
        assertFalse(SourceFailurePolicy.looksLikeSourceProblem(QueueErrorCode.TIMEOUT))
        assertFalse(SourceFailurePolicy.looksLikeSourceProblem(QueueErrorCode.DOWNGRADE))
        assertFalse(SourceFailurePolicy.looksLikeSourceProblem(QueueErrorCode.INTERNAL))
        assertFalse(SourceFailurePolicy.looksLikeSourceProblem(null))
    }
}
