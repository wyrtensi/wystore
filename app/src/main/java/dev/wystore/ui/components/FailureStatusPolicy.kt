package dev.wystore.ui.components

import dev.wystore.updates.UnverifiedSourceConsent
import dev.wystore.updates.model.QueueErrorCode

/**
 * Which kind of failure a row shows, decided from the code the queue recorded.
 *
 * A row used to be sorted by looking for "network", "timeout" and "signature" inside the failure
 * detail - an untranslated exception message from the data layer - so a storage failure was
 * "generic", anything phrased differently was "generic", and the text itself was put on the card
 * word for word. The queue has carried a typed code all along.
 */
object FailureStatusPolicy {

    fun statusFor(code: QueueErrorCode?, detail: String? = null): StatusCode = when {
        // One signature failure out of the ten is a question for the user rather than a verdict.
        UnverifiedSourceConsent.isAnswerable(code, detail) -> StatusCode.FAILED_SOURCE_UNCONFIRMED
        else -> statusForCode(code)
    }

    private fun statusForCode(code: QueueErrorCode?): StatusCode = when (code) {
        QueueErrorCode.NETWORK,
        QueueErrorCode.TIMEOUT,
        QueueErrorCode.RATE_LIMITED -> StatusCode.FAILED_NETWORK
        QueueErrorCode.STORAGE_FULL -> StatusCode.FAILED_STORAGE
        QueueErrorCode.SIGNATURE,
        QueueErrorCode.INTEGRITY -> StatusCode.FAILED_SIGNATURE
        else -> StatusCode.FAILED_GENERIC
    }
}
