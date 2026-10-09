package dev.wystore.updates

import dev.wystore.updates.model.QueueState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueRowSettlementTest {

    @Test
    fun theInstalledVersionAndOlderAreSettled() {
        assertTrue(QueueRowSettlement.isSettled(QueueState.READY_TO_INSTALL.name, 20, 20))
        assertTrue(QueueRowSettlement.isSettled(QueueState.FAILED.name, 10, 20))
    }

    @Test
    fun aNewerVersionStays() {
        // Installing v1 used to delete a v2 that was already downloaded.
        assertFalse(QueueRowSettlement.isSettled(QueueState.READY_TO_INSTALL.name, 21, 20))
        assertFalse(QueueRowSettlement.isSettled(QueueState.AVAILABLE.name, 21, 20))
    }

    @Test
    fun aRowBeingWorkedOnStays() {
        // A manual row has no version until verified; a running download is not this install's.
        assertFalse(QueueRowSettlement.isSettled(QueueState.DOWNLOADING.name, 0, 20))
        assertFalse(QueueRowSettlement.isSettled(QueueState.INSTALLING.name, 20, 20))
    }
}
