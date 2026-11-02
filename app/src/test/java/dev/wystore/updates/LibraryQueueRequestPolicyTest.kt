package dev.wystore.updates

import dev.wystore.updates.model.QueueState
import org.junit.Assert.*
import org.junit.Test

class LibraryQueueRequestPolicyTest {
    @Test fun `ordinary rediscovery retains every pinned request in the same source revision`() {
        QueueState.entries.forEach { state ->
            assertTrue(LibraryQueueRequestPolicy.retainPinned("request", 3, 3, state, manual = false))
        }
    }
    @Test fun `only a new manual available request or a new revision can replace an existing pin`() {
        assertFalse(LibraryQueueRequestPolicy.retainPinned(null, 3, 3, QueueState.AVAILABLE, false))
        assertFalse(LibraryQueueRequestPolicy.retainPinned("request", 3, 4, QueueState.CANCELED, false))
        assertFalse(LibraryQueueRequestPolicy.retainPinned("request", 3, 3, QueueState.AVAILABLE, true))
        listOf(QueueState.DOWNLOADING, QueueState.READY_TO_INSTALL, QueueState.INSTALLING).forEach { state ->
            assertTrue(LibraryQueueRequestPolicy.retainPinned("request", 3, 3, state, true))
        }
    }
}
