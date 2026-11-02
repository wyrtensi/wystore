package dev.wystore.updates

import dev.wystore.updates.model.QueueState

object LibraryQueueRequestPolicy {
    fun retainPinned(request: String?, revision: Long, requestedRevision: Long, state: QueueState, manual: Boolean): Boolean =
        request != null && revision == requestedRevision && (!manual || state != QueueState.AVAILABLE)
}
