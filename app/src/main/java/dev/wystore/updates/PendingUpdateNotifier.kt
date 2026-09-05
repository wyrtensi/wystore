package dev.wystore.updates

import android.content.Context
import dev.wystore.background.NotificationCoordinator
import dev.wystore.data.PendingUpdate
import dev.wystore.updates.model.QueueItemSnapshot
import dev.wystore.updates.model.QueueState

/**
 * Keeps the single "ready to install" notification in step with the queue.
 *
 * Every call renders the whole pending set rather than adding or removing one entry: showing an
 * update used to post its own notification and installing it cancelled that one by id, so a bulk
 * download left a column of separate notifications in the shade and any that failed to be
 * cancelled stayed there for good.
 */
class PendingUpdateNotifier(
    context: Context,
    private val queueRepository: QueueRepository = QueueRepository.getInstance(context)
) {
    private val coordinator = NotificationCoordinator(context)

    /** Re-renders from the queue. Reads the database, so callers must be off the main thread. */
    suspend fun refresh() {
        coordinator.publishReady(queueRepository.getPendingUpdates().map { it.toSnapshot() })
    }

    private fun PendingUpdate.toSnapshot() = QueueItemSnapshot(
        id = packageName,
        packageName = packageName,
        label = label,
        versionName = versionName,
        versionCode = versionCode,
        source = source,
        state = QueueState.READY_TO_INSTALL,
        priority = 0,
        position = 0
    )
}
