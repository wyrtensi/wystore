package dev.wystore.updates

import dev.wystore.data.QueueMode
import dev.wystore.updates.model.QueueAction
import dev.wystore.updates.model.QueueItemSnapshot

object QueueCoordinatorPolicy {
    fun determineNextAction(
        mode: QueueMode,
        justInstalledId: String,
        nextEligible: QueueItemSnapshot?
    ): QueueAction? {
        return if (mode == QueueMode.SMART_PROMPTS && nextEligible != null) {
            QueueAction.OfferNext
        } else {
            null
        }
    }

    /**
     * Whether the prompt has to be put back after a cold start.
     *
     * It covers one gap: the process died between an install finishing and the user answering. The
     * condition used to be "a finished install exists", which stays true for the life of the row, so
     * the prompt came back on every launch for as long as anything sat in the queue.
     *
     * [finishedAt] is when the most recent install finished, [lastAnsweredAt] when the user last
     * accepted or dismissed an offer.
     */
    fun shouldRestoreOffer(
        finishedAt: Long?,
        lastAnsweredAt: Long,
        now: Long,
        windowMillis: Long = OFFER_WINDOW_MILLIS
    ): Boolean {
        if (finishedAt == null) return false
        if (finishedAt <= lastAnsweredAt) return false
        return now - finishedAt <= windowMillis
    }

    /**
     * How long an unanswered offer stays worth restoring. Long enough to cover a restart, short
     * enough that a prompt never arrives out of nowhere.
     */
    const val OFFER_WINDOW_MILLIS = 10 * 60 * 1000L
}
