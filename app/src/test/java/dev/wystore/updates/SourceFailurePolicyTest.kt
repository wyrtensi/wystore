package dev.wystore.updates

import dev.wystore.updates.model.QueueErrorCode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceFailurePolicyTest {

    /** The three that mean "the page answered wrong, too often, or not at all". */
    @Test
    fun `the source answering badly is the source's problem`() {
        assertTrue(SourceFailurePolicy.looksLikeSourceProblem(QueueErrorCode.SOURCE_CHANGED))
        assertTrue(SourceFailurePolicy.looksLikeSourceProblem(QueueErrorCode.RATE_LIMITED))
        assertTrue(SourceFailurePolicy.looksLikeSourceProblem(QueueErrorCode.TIMEOUT))
    }

    /**
     * A dropped connection is the commonest failure there is and almost never the source. Offering
     * to update the store over it would cry wolf often enough that the real alarm gets ignored.
     */
    @Test
    fun `a lost connection is not`() {
        assertFalse(SourceFailurePolicy.looksLikeSourceProblem(QueueErrorCode.NETWORK))
    }

    @Test
    fun `neither is anything decided on this phone`() {
        assertFalse(SourceFailurePolicy.looksLikeSourceProblem(QueueErrorCode.STORAGE_FULL))
        assertFalse(SourceFailurePolicy.looksLikeSourceProblem(QueueErrorCode.SIGNATURE))
        assertFalse(SourceFailurePolicy.looksLikeSourceProblem(QueueErrorCode.INSTALL_CANCELED))
        assertFalse(SourceFailurePolicy.looksLikeSourceProblem(QueueErrorCode.DOWNGRADE))
        assertFalse(SourceFailurePolicy.looksLikeSourceProblem(QueueErrorCode.INTERNAL))
        assertFalse(SourceFailurePolicy.looksLikeSourceProblem(null))
    }
}
