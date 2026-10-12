package dev.wystore.updates

import dev.wystore.updates.model.QueueState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueRecoveryPolicyTest {

    private val now = 100 * 60 * 1000L

    @Test
    fun aHandoverLeftLongAgoIsAbandoned() {
        assertTrue(
            QueueRecoveryPolicy.isAbandonedHandover(
                QueueState.AWAITING_USER_CONFIRMATION,
                updatedAt = now - QueueRecoveryPolicy.HANDOVER_TIMEOUT_MILLIS,
                now = now
            )
        )
    }

    @Test
    fun aHandoverStillBeingWrittenIsLeftAlone() {
        assertFalse(
            QueueRecoveryPolicy.isAbandonedHandover(QueueState.AWAITING_USER_CONFIRMATION, updatedAt = now - 5_000, now = now)
        )
    }

    @Test
    fun otherStatesAreNotHandovers() {
        assertFalse(QueueRecoveryPolicy.isAbandonedHandover(QueueState.INSTALLING, updatedAt = 0, now = now))
        assertFalse(QueueRecoveryPolicy.isAbandonedHandover(QueueState.READY_TO_INSTALL, updatedAt = 0, now = now))
    }
}
