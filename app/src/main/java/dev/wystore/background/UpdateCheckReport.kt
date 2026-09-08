package dev.wystore.background

import androidx.work.Data
import androidx.work.workDataOf

/** How a finished check should be summed up in one line. */
enum class CheckOutcome {
    UP_TO_DATE,
    UPDATES_FOUND,
    PROBLEMS
}

/**
 * What a check tells the screen it was started from.
 *
 * The worker used to publish nothing at all: no progress while it ran, and a bare
 * Result.success() when it finished. The ViewModel reads "detail" out of progress and then out of
 * output data, found neither, and so a manual check ended by silently removing its own progress
 * card - pressing "check" looked like pressing nothing.
 */
object UpdateCheckReport {

    const val KEY_STATUS = "status"
    const val KEY_DETAIL = "detail"
    const val KEY_CHECKED = "checked"
    const val KEY_TOTAL = "total"
    const val KEY_UPDATES = "updates"

    const val STATUS_CHECKING = "CHECKING"
    const val STATUS_COMPLETE = "COMPLETE"

    fun progress(checked: Int, total: Int, updates: Int, detail: String? = null): Data =
        workDataOf(
            KEY_STATUS to STATUS_CHECKING,
            KEY_CHECKED to checked,
            KEY_TOTAL to total,
            KEY_UPDATES to updates,
            KEY_DETAIL to detail
        )

    fun result(checked: Int, total: Int, updates: Int, detail: String): Data =
        workDataOf(
            KEY_STATUS to STATUS_COMPLETE,
            KEY_CHECKED to checked,
            KEY_TOTAL to total,
            KEY_UPDATES to updates,
            KEY_DETAIL to detail
        )

    /**
     * Problems come first: an update found in the half of the library that answered says nothing
     * about the half that did not, and reporting only the good half reads as a clean bill.
     */
    fun outcome(updates: Int, problems: Int): CheckOutcome = when {
        problems > 0 -> CheckOutcome.PROBLEMS
        updates > 0 -> CheckOutcome.UPDATES_FOUND
        else -> CheckOutcome.UP_TO_DATE
    }
}
