package dev.wystore.background

import dev.wystore.updates.QueueRecoveryPolicy
import dev.wystore.updates.model.QueueState

/**
 * What a transfer runner does with its row when it is stopped.
 *
 * Pause, cancel and skip relabel the row before stopping the work, so the row says who decided.
 * A row still in flight was stopped by nobody's decision - the system ran out of time, lost the
 * network, or replaced the work - and goes back in line with its bytes. Both runners share this:
 * the user-initiated job used to rethrow without it, so on Android 14 and later a stopped download
 * kept its row at DOWNLOADING, holding the slot with nothing carrying it.
 */
object RunnerStopPolicy {
    enum class Outcome { RELEASE, DELETE_FILES, NOTHING }

    fun onStopped(state: QueueState?): Outcome = when (state) {
        in QueueRecoveryPolicy.INTERRUPTIBLE_STATES -> Outcome.RELEASE
        QueueState.CANCELED, QueueState.SKIPPED -> Outcome.DELETE_FILES
        else -> Outcome.NOTHING
    }
}
