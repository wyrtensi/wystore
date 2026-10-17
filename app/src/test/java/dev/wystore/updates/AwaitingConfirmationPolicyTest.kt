package dev.wystore.updates

import dev.wystore.updates.model.QueueState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AwaitingConfirmationPolicyTest {

    private val awaiting = InstallSessionWriter.SESSION_AWAITING_USER

    @Test
    fun anInstallWaitingOnTheDialogIsAwaiting() {
        assertTrue(AwaitingConfirmationPolicy.isAwaiting(QueueState.INSTALLING.name, awaiting))
    }

    @Test
    fun anInstallNobodyWasAskedAboutIsNot() {
        // Silent and root installs never wait on a dialog; calling them off would cut an install
        // Android is carrying out.
        assertFalse(AwaitingConfirmationPolicy.isAwaiting(QueueState.INSTALLING.name, InstallSessionWriter.SESSION_PREPARED))
        assertFalse(AwaitingConfirmationPolicy.isAwaiting(QueueState.INSTALLING.name, null))
    }

    @Test
    fun aRowThatMovedOnIsNot() {
        assertFalse(AwaitingConfirmationPolicy.isAwaiting(QueueState.READY_TO_INSTALL.name, awaiting))
        assertFalse(AwaitingConfirmationPolicy.isAwaiting(QueueState.INSTALLED.name, awaiting))
    }

    @Test
    fun anAnswerAlreadyGivenIsSeenOnThePhone() {
        // Install was pressed and the app is there; cancelling now would report a failure for it.
        assertTrue(AwaitingConfirmationPolicy.wasInstalled(100, installedVersionCode = 100, installedUpdatedAt = 2_000, askedAt = 1_000))
    }

    @Test
    fun aReinstallOfTheSameVersionIsNotTakenAsAnswered() {
        // The version asked for was on the phone before the question: only a newer write counts.
        assertFalse(AwaitingConfirmationPolicy.wasInstalled(100, installedVersionCode = 100, installedUpdatedAt = 500, askedAt = 1_000))
    }

    @Test
    fun anOlderOrMissingAppIsNotAnswered() {
        assertFalse(AwaitingConfirmationPolicy.wasInstalled(100, installedVersionCode = 99, installedUpdatedAt = 2_000, askedAt = 1_000))
        assertFalse(AwaitingConfirmationPolicy.wasInstalled(100, installedVersionCode = null, installedUpdatedAt = null, askedAt = 1_000))
    }

    @Test
    fun aLateAbortForADroppedSessionIsStale() {
        assertTrue(AwaitingConfirmationPolicy.isStaleResult(11, currentSessionId = 12, succeeded = false, abandoned = false))
    }

    @Test
    fun anAbortForASessionCalledOffIsStaleEvenWithNothingToCompare() {
        // The new session is still being written, so the row has none yet.
        assertTrue(AwaitingConfirmationPolicy.isStaleResult(11, currentSessionId = null, succeeded = false, abandoned = true))
    }

    @Test
    fun theCurrentSessionsResultIsNotStale() {
        assertFalse(AwaitingConfirmationPolicy.isStaleResult(12, currentSessionId = 12, succeeded = false, abandoned = false))
    }

    @Test
    fun aResultWithNothingToCompareIsNotStale() {
        // No session left on the row, or a callback from a build that did not name its session:
        // nothing says it belongs elsewhere, so it is applied as before.
        assertFalse(AwaitingConfirmationPolicy.isStaleResult(11, currentSessionId = null, succeeded = false, abandoned = false))
        assertFalse(AwaitingConfirmationPolicy.isStaleResult(-1, currentSessionId = 12, succeeded = false, abandoned = false))
    }

    @Test
    fun aSuccessIsNeverStale() {
        assertFalse(AwaitingConfirmationPolicy.isStaleResult(11, currentSessionId = 12, succeeded = true, abandoned = true))
    }
}
