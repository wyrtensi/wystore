package dev.wystore.updates

import dev.wystore.InstallQueueStatus
import dev.wystore.updates.model.QueueErrorCode

object QueueRetryFeedback {
    // Automatic retries return to the queue with their diagnosis intact. Hiding it made a timed
    // out download look as though the user had never pressed Download now.
    fun errorCode(status: InstallQueueStatus, code: QueueErrorCode?): QueueErrorCode? =
        code?.takeIf {
            status == InstallQueueStatus.QUEUED &&
                (it == QueueErrorCode.NETWORK || it == QueueErrorCode.NETWORK_TIMEOUT ||
                    it == QueueErrorCode.TIMEOUT)
        }
}
