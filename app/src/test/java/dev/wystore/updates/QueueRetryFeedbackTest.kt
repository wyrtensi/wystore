package dev.wystore.updates

import dev.wystore.InstallQueueStatus
import dev.wystore.updates.model.QueueErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueRetryFeedbackTest {
    @Test
    fun aQueuedRetryKeepsTheConnectionFailureVisible() {
        for (code in listOf(QueueErrorCode.NETWORK, QueueErrorCode.NETWORK_TIMEOUT, QueueErrorCode.TIMEOUT)) {
            assertEquals(code, QueueRetryFeedback.errorCode(InstallQueueStatus.QUEUED, code))
        }
    }

    @Test
    fun aFreshQueueItemHasNoFailureHint() {
        assertNull(QueueRetryFeedback.errorCode(InstallQueueStatus.QUEUED, null))
    }

    @Test
    fun connectionAdviceIsNotShownForAnUnrelatedFailure() {
        for (code in QueueErrorCode.entries.filterNot {
            it == QueueErrorCode.NETWORK || it == QueueErrorCode.NETWORK_TIMEOUT || it == QueueErrorCode.TIMEOUT
        }) {
            assertNull(QueueRetryFeedback.errorCode(InstallQueueStatus.QUEUED, code))
        }
    }

    @Test
    fun thePreviousFailureIsHiddenOnceTheNextAttemptStartsOrFinishes() {
        for (status in InstallQueueStatus.entries.filterNot { it == InstallQueueStatus.QUEUED }) {
            for (code in listOf(QueueErrorCode.NETWORK, QueueErrorCode.NETWORK_TIMEOUT, QueueErrorCode.TIMEOUT)) {
                assertNull("$status / $code", QueueRetryFeedback.errorCode(status, code))
            }
        }
    }
}
