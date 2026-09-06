package dev.wystore.updates

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the "install the next one?" prompt is put back after a cold start.
 *
 * It exists for one situation: the process died between an install finishing and the user
 * answering. The condition used to be "the database contains a finished install", which is true
 * forever, so the prompt reappeared on every launch for as long as anything was left in the queue,
 * days after the install it was supposedly following.
 */
class OfferNextRestoreTest {

    private val now = 1_700_000_000_000L

    @Test
    fun anInstallThatJustFinishedIsStillWaitingForAnAnswer() {
        assertTrue(
            QueueCoordinatorPolicy.shouldRestoreOffer(
                finishedAt = now - 30_000,
                lastAnsweredAt = 0L,
                now = now
            )
        )
    }

    @Test
    fun anOldInstallIsNotSomethingTheUserIsStillAnswering() {
        assertFalse(
            QueueCoordinatorPolicy.shouldRestoreOffer(
                finishedAt = now - 24 * 60 * 60 * 1000L,
                lastAnsweredAt = 0L,
                now = now
            )
        )
    }

    @Test
    fun anAnsweredOfferDoesNotComeBack() {
        val finishedAt = now - 60_000
        assertFalse(
            QueueCoordinatorPolicy.shouldRestoreOffer(
                finishedAt = finishedAt,
                lastAnsweredAt = finishedAt + 1_000,
                now = now
            )
        )
    }

    @Test
    fun aNewerInstallAsksAgainEvenAfterAnEarlierAnswer() {
        assertTrue(
            QueueCoordinatorPolicy.shouldRestoreOffer(
                finishedAt = now - 10_000,
                lastAnsweredAt = now - 120_000,
                now = now
            )
        )
    }

    @Test
    fun nothingHasFinishedSoThereIsNothingToRestore() {
        assertFalse(
            QueueCoordinatorPolicy.shouldRestoreOffer(
                finishedAt = null,
                lastAnsweredAt = 0L,
                now = now
            )
        )
    }

    @Test
    fun aClockThatWentBackwardsDoesNotStrandThePrompt() {
        // Timestamps come from the device clock; a row stamped in the future must not be treated as
        // permanently fresh.
        assertTrue(
            QueueCoordinatorPolicy.shouldRestoreOffer(
                finishedAt = now + 60_000,
                lastAnsweredAt = 0L,
                now = now
            )
        )
    }
}
