package dev.wystore.updates

import dev.wystore.data.local.UpdateQueueEntity

/** Intent already recorded on a row survives stale label, progress and source metadata writes. */
object QueueRequestRetention {
    fun merge(incoming: UpdateQueueEntity, vararg stored: UpdateQueueEntity?): UpdateQueueEntity {
        val ownershipRequested = incoming.requiresUpdateOwnership || stored.any { it?.requiresUpdateOwnership == true }
        return incoming.copy(
            priority = maxOf(
                incoming.priority,
                stored.filterNotNull().maxOfOrNull { it.priority } ?: incoming.priority,
                if (ownershipRequested) QueueOrigin.USER_REQUESTED_PRIORITY else incoming.priority
            ),
            requiresUpdateOwnership = ownershipRequested
        )
    }
}
