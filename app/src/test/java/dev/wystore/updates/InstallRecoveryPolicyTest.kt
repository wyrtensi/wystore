package dev.wystore.updates

import org.junit.Assert.assertEquals
import org.junit.Test

class InstallRecoveryPolicyTest {
    @Test fun `a live installer is never displaced even before Android creates its session`() {
        assertEquals(InstallRecoveryPolicy.Decision.LEAVE,
            InstallRecoveryPolicy.decide("ROOT_CLAIMED", activeAttempt = true, exists = false, sealed = false))
    }

    @Test fun `a dead root claim cannot commit and can return to ready`() {
        assertEquals(InstallRecoveryPolicy.Decision.RETURN_TO_READY,
            InstallRecoveryPolicy.decide("ROOT_CLAIMED", false, false, false))
    }

    @Test fun `the committed journal marker alone does not prove Android started installation`() {
        listOf("COMMITTED", "ROOT_COMMITTED", "ROOT_PREPARED").forEach { stage ->
            assertEquals(InstallRecoveryPolicy.Decision.ABANDON_THEN_OBSERVE,
                InstallRecoveryPolicy.decide(stage, false, true, false))
            assertEquals(InstallRecoveryPolicy.Decision.LEAVE,
                InstallRecoveryPolicy.decide(stage, false, true, true))
        }
    }

    @Test fun `a closed session is reconciled but an unanswered live dialog keeps its grace`() {
        assertEquals(InstallRecoveryPolicy.Decision.RECONCILE_CLOSED,
            InstallRecoveryPolicy.decide("AWAITING_USER", false, false, true))
        assertEquals(InstallRecoveryPolicy.Decision.LEAVE,
            InstallRecoveryPolicy.decide("AWAITING_USER", false, true, true))
    }
}
