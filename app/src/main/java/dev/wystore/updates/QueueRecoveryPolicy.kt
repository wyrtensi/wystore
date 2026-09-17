package dev.wystore.updates

import dev.wystore.updates.model.QueueState

/**
 * Which queue rows are left holding the transfer slot with nothing carrying them.
 *
 * The queue moves one transfer at a time, and a row marked as in flight keeps every other row
 * waiting. When the process dies mid-download, or a background job is stopped before it can write
 * anything, the row keeps saying it is downloading. The work that reruns cannot claim a row in that
 * state, so it finishes at once, and everything a background check found stayed queued until the
 * user pressed a button.
 */
object QueueRecoveryPolicy {

    /** The states a runner holds a row in, and so the ones a lost runner leaves it in. */
    val INTERRUPTIBLE_STATES: Set<QueueState> = setOf(
        QueueState.CHECKING,
        QueueState.DOWNLOADING,
        QueueState.VERIFYING
    )

    /** A row is orphaned when it says it is being carried and no scheduled work is carrying it. */
    fun isOrphaned(state: QueueState, workScheduled: Boolean, jobScheduled: Boolean): Boolean =
        state in INTERRUPTIBLE_STATES && !workScheduled && !jobScheduled
}
