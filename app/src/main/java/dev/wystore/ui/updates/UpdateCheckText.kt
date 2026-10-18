package dev.wystore.ui.updates

import dev.wystore.R
import dev.wystore.UpdateCheckTask

/** What a running update check is called, and the one line that says how far it has got. */
object UpdateCheckText {

    fun titleRes(status: String): Int = when (status) {
        "QUEUED" -> R.string.update_check_queued
        "CHECKING" -> R.string.update_check_checking
        "FOUND" -> R.string.update_check_found
        "READY" -> R.string.update_check_ready
        "DOWNLOADING" -> R.string.update_check_downloading
        "VERIFYING" -> R.string.queue_status_verifying
        "DOWNLOADED" -> R.string.update_check_downloaded
        "INSTALLING" -> R.string.update_check_installing
        "COMPLETE" -> R.string.update_check_complete
        "CURRENT" -> R.string.update_check_current
        "INSTALLED" -> R.string.update_check_installed
        "SKIPPED" -> R.string.update_check_skipped
        "ERROR", "FAILED" -> R.string.update_check_error
        else -> R.string.update_check_current
    }

    /**
     * The count once the check knows how many apps it has, the worker's own detail before that.
     * Both at once was the count twice: the detail is "0 of 7" while the apps are counted.
     */
    fun showsCount(task: UpdateCheckTask): Boolean = task.total > 0
}
