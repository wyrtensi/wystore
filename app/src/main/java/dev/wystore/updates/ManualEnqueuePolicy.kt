package dev.wystore.updates

import dev.wystore.updates.model.QueueState

/** What a tap on Install or Update should do with the rows the queue already holds. */
enum class ManualEnqueueAction {
    /** No row stands in the way: enqueue and transfer. */
    START,

    /** A stopped row has to go back to AVAILABLE first, or the transfer cannot legally begin. */
    RESET_AND_START,

    /** The queue is already working on it, or already holds the finished download. */
    IGNORE
}

/**
 * Reconciles a manual install request with the durable queue.
 *
 * The reducer accepts StartDownload only from AVAILABLE or CHECKING. Manual installs used to be
 * enqueued on top of whatever state the row held, so a package that had failed once made every
 * later attempt throw on its first transition — reported to the user as a network error, forever.
 */
object ManualEnqueuePolicy {

    fun decide(states: List<QueueState>): ManualEnqueueAction = when {
        states.any { it in BUSY } -> ManualEnqueueAction.IGNORE
        states.any { it == QueueState.AVAILABLE } -> ManualEnqueueAction.START
        states.isEmpty() -> ManualEnqueueAction.START
        else -> ManualEnqueueAction.RESET_AND_START
    }

    /**
     * States in which the queue speaks for the package already: a transfer is running, or the
     * verified artifact is on disk waiting for the install the user is about to be offered.
     */
    private val BUSY = setOf(
        QueueState.CHECKING,
        QueueState.DOWNLOADING,
        QueueState.VERIFYING,
        QueueState.INSTALLING,
        QueueState.READY_TO_INSTALL,
        QueueState.AWAITING_UNKNOWN_SOURCES_PERMISSION,
        QueueState.AWAITING_USER_CONFIRMATION
    )
}
