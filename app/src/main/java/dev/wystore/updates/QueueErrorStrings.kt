package dev.wystore.updates

import dev.wystore.R
import dev.wystore.updates.model.QueueErrorCode

/**
 * The sentence shown for a failed queue item.
 *
 * The mapping used to live inside a composable, so a notification - which has no composition to
 * read resources from - could only say "some updates could not be installed" and leave the reason
 * behind in the app.
 */
object QueueErrorStrings {
    fun stringRes(code: QueueErrorCode): Int = when (code) {
        QueueErrorCode.NETWORK -> R.string.queue_error_network
        QueueErrorCode.RATE_LIMITED -> R.string.queue_error_rate_limited
        QueueErrorCode.SOURCE_CHANGED -> R.string.queue_error_source_changed
        QueueErrorCode.STORAGE_FULL -> R.string.queue_error_storage_full
        QueueErrorCode.INTEGRITY -> R.string.queue_error_integrity
        QueueErrorCode.SIGNATURE -> R.string.queue_error_signature
        QueueErrorCode.INCOMPATIBLE -> R.string.queue_error_incompatible
        QueueErrorCode.PERMISSION -> R.string.queue_error_permission
        QueueErrorCode.INSTALL_CANCELED -> R.string.queue_error_install_canceled
        QueueErrorCode.INSTALL_FAILED -> R.string.queue_error_install_failed
        QueueErrorCode.ARTIFACT_MISSING -> R.string.queue_error_artifact_missing
        QueueErrorCode.TIMEOUT -> R.string.queue_error_timeout
        QueueErrorCode.DOWNGRADE -> R.string.queue_error_downgrade
        QueueErrorCode.INTERNAL -> R.string.queue_error_internal
    }
}
