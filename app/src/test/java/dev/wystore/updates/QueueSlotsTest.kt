package dev.wystore.updates

import dev.wystore.updates.model.QueueState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueSlotsTest {

    @Test
    fun downloadingAndVerifyingShareTheTransferSlot() {
        assertEquals(QueueSlots.Slot.TRANSFER, QueueSlots.slotOf(QueueState.DOWNLOADING.name))
        assertEquals(QueueSlots.Slot.TRANSFER, QueueSlots.slotOf(QueueState.VERIFYING.name))
    }

    @Test
    fun installingHasASlotOfItsOwn() {
        // One slot for both made every install wait until nothing was downloading, and an
        // unanswered install dialog stopped every download behind it.
        assertEquals(QueueSlots.Slot.INSTALL, QueueSlots.slotOf(QueueState.INSTALLING.name))
    }

    @Test
    fun aDownloadAndAnInstallDoNotConflict() {
        assertTrue(QueueSlots.statesInSlotOf(QueueState.DOWNLOADING.name).none { it == QueueState.INSTALLING.name })
        assertTrue(QueueSlots.statesInSlotOf(QueueState.INSTALLING.name).none { it == QueueState.DOWNLOADING.name })
    }

    @Test
    fun waitingStatesHoldNoSlot() {
        listOf(QueueState.AVAILABLE, QueueState.READY_TO_INSTALL, QueueState.AWAITING_USER_CONFIRMATION, QueueState.PAUSED, QueueState.FAILED)
            .forEach { assertNull(it.name, QueueSlots.slotOf(it.name)) }
        assertTrue(QueueSlots.statesInSlotOf(QueueState.READY_TO_INSTALL.name).isEmpty())
    }

    @Test
    fun enteringASlotIsOnlyCheckedWhenTheRowWasNotInItAlready() {
        assertTrue(QueueSlots.entersSlot(from = QueueState.AVAILABLE.name, to = QueueState.DOWNLOADING.name))
        assertFalse(QueueSlots.entersSlot(from = QueueState.DOWNLOADING.name, to = QueueState.VERIFYING.name))
        assertTrue(QueueSlots.entersSlot(from = QueueState.AWAITING_USER_CONFIRMATION.name, to = QueueState.INSTALLING.name))
        assertFalse(QueueSlots.entersSlot(from = QueueState.INSTALLING.name, to = QueueState.INSTALLED.name))
    }
}
