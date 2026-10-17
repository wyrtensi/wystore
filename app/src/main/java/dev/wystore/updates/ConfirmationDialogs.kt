package dev.wystore.updates

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Installs waiting for an answer in Android's confirmation dialog, by queue row, with the dialog
 * itself when this process still has it.
 *
 * A dialog closed by a tap beside it, by Home or from recents can leave the session waiting without
 * a word back, depending on the firmware. The row then said "Installing" with nothing to press until
 * [StuckInstallPolicy] gave up on it a minute later. Kept here so the row can offer the same dialog
 * again, or a cancel, as soon as the app is back in front.
 *
 * The dialog's Intent does not outlive the process. A row found waiting after a restart is listed
 * without one, and can only be cancelled.
 */
object ConfirmationDialogs {

    private val dialogs = MutableStateFlow<Map<String, Intent?>>(emptyMap())

    val waiting: StateFlow<Map<String, Intent?>> = dialogs.asStateFlow()

    fun opened(queueId: String, dialog: Intent) = dialogs.update { it + (queueId to Intent(dialog)) }

    /** A row the database says is waiting; keeps the dialog if this process already has it. */
    fun noticed(queueId: String) = dialogs.update { if (queueId in it) it else it + (queueId to null) }

    fun dialogFor(queueId: String): Intent? = dialogs.value[queueId]

    fun closed(queueId: String) = dialogs.update { if (queueId in it) it - queueId else it }

    /** Sessions Wy Store called off itself; see [AwaitingConfirmationPolicy.isStaleResult]. */
    private val calledOff = java.util.Collections.synchronizedSet(mutableSetOf<Int>())

    fun calledOff(sessionId: Int) {
        calledOff += sessionId
    }

    fun wasCalledOff(sessionId: Int): Boolean = sessionId in calledOff
}
