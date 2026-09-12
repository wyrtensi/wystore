package dev.wystore.ui.components

import dev.wystore.data.VerificationError
import dev.wystore.updates.model.QueueErrorCode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Failures used to be sorted by looking for English words inside an exception message, so a row
 * was "generic" whenever the wording was not the one being searched for - and that wording was
 * then shown to the user as the failure itself.
 */
class FailureStatusPolicyTest {

    @Test
    fun `the typed code decides, not the words in the detail`() {
        assertEquals(
            StatusCode.FAILED_NETWORK,
            FailureStatusPolicy.statusFor(QueueErrorCode.TIMEOUT, "соединение прервано")
        )
        assertEquals(
            StatusCode.FAILED_STORAGE,
            FailureStatusPolicy.statusFor(QueueErrorCode.STORAGE_FULL, "ENOSPC")
        )
        assertEquals(
            StatusCode.FAILED_SIGNATURE,
            FailureStatusPolicy.statusFor(QueueErrorCode.INTEGRITY, "hash mismatch")
        )
    }

    @Test
    fun `a failure of the app itself is not sorted as anything else`() {
        assertEquals(
            StatusCode.FAILED_GENERIC,
            FailureStatusPolicy.statusFor(
                QueueErrorCode.INTERNAL,
                "Action is not allowed while queue item 8f2c1cc7 is DOWNLOADING."
            )
        )
    }

    @Test
    fun `the source refusing to vouch for its own file is a question`() {
        assertEquals(
            StatusCode.FAILED_SOURCE_UNCONFIRMED,
            FailureStatusPolicy.statusFor(
                QueueErrorCode.SIGNATURE,
                VerificationError.SOURCE_FINGERPRINT_MISMATCH.name
            )
        )
    }

    @Test
    fun `every other signature failure stays a verdict`() {
        listOf(
            VerificationError.SIGNATURE_MISMATCH,
            VerificationError.MIXED_SIGNATURES,
            VerificationError.UNREADABLE_SIGNATURE
        ).forEach { error ->
            assertEquals(
                "$error must not be offered as a choice",
                StatusCode.FAILED_SIGNATURE,
                FailureStatusPolicy.statusFor(QueueErrorCode.SIGNATURE, error.name)
            )
        }
    }

    @Test
    fun `a row with no code at all still says something`() {
        assertEquals(StatusCode.FAILED_GENERIC, FailureStatusPolicy.statusFor(null, null))
    }
}
