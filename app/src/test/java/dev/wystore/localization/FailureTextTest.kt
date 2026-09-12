package dev.wystore.localization

import dev.wystore.R
import dev.wystore.data.VerificationError
import dev.wystore.ui.components.StatusCode
import dev.wystore.ui.components.StatusMessage
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What a failed row is allowed to say.
 *
 * Two untranslated strings reached the screen this way: an exception message from the queue's own
 * state machine, and the name of a verification constant. Both codes have had a sentence in
 * resources all along; the selection between them is what is pinned here, without a Context.
 */
class FailureTextTest {

    @Test
    fun `the verification reason is preferred, because it says which check refused the file`() {
        val message = StatusMessage(
            code = StatusCode.FAILED_SIGNATURE,
            args = mapOf(
                StatusMessage.ARG_ERROR_CODE to "SIGNATURE",
                StatusMessage.ARG_VERIFICATION_ERROR to VerificationError.DOWNGRADE.name
            )
        )

        assertEquals(R.string.verify_downgrade, StatusTextResolver.failureStringRes(message))
    }

    @Test
    fun `without one, the queue's own code answers`() {
        val message = StatusMessage(
            code = StatusCode.FAILED_GENERIC,
            args = mapOf(StatusMessage.ARG_ERROR_CODE to "INTERNAL")
        )

        assertEquals(R.string.queue_error_internal, StatusTextResolver.failureStringRes(message))
    }

    @Test
    fun `a row that can be answered asks its question rather than naming the check`() {
        val message = StatusMessage(
            code = StatusCode.FAILED_SOURCE_UNCONFIRMED,
            args = mapOf(
                StatusMessage.ARG_ERROR_CODE to "SIGNATURE",
                StatusMessage.ARG_VERIFICATION_ERROR to VerificationError.SOURCE_FINGERPRINT_MISMATCH.name
            )
        )

        assertEquals(R.string.unverified_source_status, StatusTextResolver.failureStringRes(message))
    }

    @Test
    fun `a row carrying nothing still has a sentence`() {
        assertEquals(
            R.string.status_failed_generic,
            StatusTextResolver.failureStringRes(StatusMessage(StatusCode.FAILED_GENERIC))
        )
    }

    @Test
    fun `an unknown code name is not passed through as text`() {
        val message = StatusMessage(
            code = StatusCode.FAILED_GENERIC,
            args = mapOf(StatusMessage.ARG_ERROR_CODE to "SOMETHING_NEW")
        )

        assertEquals(R.string.status_failed_generic, StatusTextResolver.failureStringRes(message))
    }
}
