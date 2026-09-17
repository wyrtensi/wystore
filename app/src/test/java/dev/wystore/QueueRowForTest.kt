package dev.wystore

import dev.wystore.updates.QueueRecoveryPolicy
import dev.wystore.updates.model.QueueState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Updates a background check found were downloading or waiting, and the app's card still showed
 * "Open": the first row for the package in queue order was an old, finished one.
 */
class QueueRowForTest {

    private fun row(id: String, status: InstallQueueStatus, packageName: String = "ru.app") =
        InstallQueueItem(id = id, packageName = packageName, label = id, status = status)

    @Test
    fun `the row being worked on wins over finished history in front of it`() {
        val queue = listOf(
            row("old-finished", InstallQueueStatus.COMPLETE),
            row("old-canceled", InstallQueueStatus.CANCELED),
            row("found-by-check", InstallQueueStatus.QUEUED)
        )
        assertEquals("found-by-check", queue.rowFor("ru.app")?.id)
    }

    @Test
    fun `a downloaded row and a paused one count as current`() {
        assertEquals("ready", listOf(row("done", InstallQueueStatus.COMPLETE), row("ready", InstallQueueStatus.READY)).rowFor("ru.app")?.id)
        assertEquals("paused", listOf(row("done", InstallQueueStatus.COMPLETE), row("paused", InstallQueueStatus.PAUSED)).rowFor("ru.app")?.id)
    }

    @Test
    fun `a failure outranks history, and history is still returned when that is all there is`() {
        assertEquals("failed", listOf(row("done", InstallQueueStatus.COMPLETE), row("failed", InstallQueueStatus.FAILED)).rowFor("ru.app")?.id)
        assertEquals("done", listOf(row("done", InstallQueueStatus.COMPLETE)).rowFor("ru.app")?.id)
        assertNull(listOf(row("other", InstallQueueStatus.QUEUED, "ru.other")).rowFor("ru.app"))
    }

    @Test
    fun `a row left in flight with nothing scheduled for it is orphaned`() {
        assertTrue(QueueRecoveryPolicy.isOrphaned(QueueState.DOWNLOADING, workScheduled = false, jobScheduled = false))
        assertFalse(QueueRecoveryPolicy.isOrphaned(QueueState.DOWNLOADING, workScheduled = true, jobScheduled = false))
        assertFalse(QueueRecoveryPolicy.isOrphaned(QueueState.DOWNLOADING, workScheduled = false, jobScheduled = true))
        assertFalse(QueueRecoveryPolicy.isOrphaned(QueueState.PAUSED, workScheduled = false, jobScheduled = false))
        assertFalse(QueueRecoveryPolicy.isOrphaned(QueueState.READY_TO_INSTALL, workScheduled = false, jobScheduled = false))
    }
}
