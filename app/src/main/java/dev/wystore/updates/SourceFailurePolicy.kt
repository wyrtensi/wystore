package dev.wystore.updates

import dev.wystore.updates.model.QueueErrorCode

/**
 * Whether a failed transfer looks like the source's doing rather than the phone's.
 *
 * RuStore is read through its public pages, and those change without warning. When they do, every
 * download fails the same way at once and there is nothing the user can do about it except wait
 * for a build that speaks the new shape - or say that it is broken, which is the more useful of
 * the two and the one nobody was offered.
 *
 * Deliberately narrow. A plain network failure is almost always the connection in the user's hand,
 * and offering to update the store over a lost Wi-Fi signal would turn a hiccup into a false alarm
 * and teach people to ignore the one that matters.
 */
object SourceFailurePolicy {

    fun looksLikeSourceProblem(code: QueueErrorCode?): Boolean = when (code) {
        // The page answered, and did not answer the way this build expects.
        QueueErrorCode.SOURCE_CHANGED,
        // Answered with "not now" - a limit on their side, not a setting on this one.
        QueueErrorCode.RATE_LIMITED,
        // Did not answer at all within the time a working source takes.
        QueueErrorCode.TIMEOUT -> true
        else -> false
    }
}
