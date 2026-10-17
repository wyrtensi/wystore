package dev.wystore.updates

import dev.wystore.updates.model.QueueState

/**
 * Which installs are waiting for the user in Android's dialog, and which install results no longer
 * belong to the row they name.
 */
object AwaitingConfirmationPolicy {

    /**
     * An install Android asked the user about and has not heard back on. Only such an install can
     * be called off from Wy Store: one Android is already carrying out - root, a silent update - is
     * left to finish.
     */
    fun isAwaiting(rowState: String, sessionState: String?): Boolean =
        rowState == QueueState.INSTALLING.name &&
            sessionState == InstallSessionWriter.SESSION_AWAITING_USER

    /**
     * Whether the dialog the user was asked in has already been answered with Install: the app on
     * the phone is at least the version asked for, and was written after the question was asked.
     * The version alone is not enough - a reinstall asks for the version already there.
     */
    fun wasInstalled(
        expectedVersionCode: Long,
        installedVersionCode: Long?,
        installedUpdatedAt: Long?,
        askedAt: Long
    ): Boolean =
        installedVersionCode != null &&
            installedVersionCode >= expectedVersionCode &&
            installedUpdatedAt != null &&
            installedUpdatedAt >= askedAt

    /**
     * A failure reported for a session the row has stopped using.
     *
     * Calling off a waiting install drops its session, and Android may report that as aborted
     * after the user has already pressed Install again. Results are addressed by row, so the late
     * report used to land on the new install and cancel it. [abandoned] covers the moment the new
     * session is still being written and the row has none to compare with. A success is never
     * ignored: an app that did install is the one fact that must not be lost.
     */
    fun isStaleResult(
        resultSessionId: Int,
        currentSessionId: Int?,
        succeeded: Boolean,
        abandoned: Boolean
    ): Boolean = when {
        succeeded -> false
        abandoned -> true
        else -> resultSessionId > 0 && currentSessionId != null && currentSessionId != resultSessionId
    }
}
