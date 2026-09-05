package dev.wystore.updates

import dev.wystore.data.QueueMode
import dev.wystore.updates.model.QueueAction
import dev.wystore.updates.model.QueueItemSnapshot

object QueueCoordinatorPolicy {
    fun determineNextAction(
        mode: QueueMode,
        justInstalledId: String,
        nextEligible: QueueItemSnapshot?
    ): QueueAction? {
        return if (mode == QueueMode.SMART_PROMPTS && nextEligible != null) {
            QueueAction.OfferNext
        } else {
            null
        }
    }
}
