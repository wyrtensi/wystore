package dev.wystore.updates

import dev.wystore.updates.model.QueueAction
import dev.wystore.updates.model.QueueItemSnapshot
import dev.wystore.updates.model.QueueState
import dev.wystore.data.ManagedSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Pausing a transfer, which is the one stop that keeps its bytes.
 *
 * The state matters beyond the label: the queue starts the next AVAILABLE row by itself, so a
 * paused download parked there would start itself again and not be paused at all.
 */
class QueuePauseReducerTest {

    private fun row(state: QueueState) = QueueItemSnapshot(
        id = "row-1",
        packageName = "dev.example.app",
        label = "Example",
        versionName = "1.0",
        versionCode = 1,
        source = ManagedSource.RUSTORE,
        state = state,
        priority = 0,
        position = 0
    )

    @Test
    fun `a running transfer can be paused`() {
        val paused = QueueReducer.reduce(row(QueueState.DOWNLOADING), QueueAction.Pause)

        assertEquals(QueueState.PAUSED, paused.state)
    }

    @Test
    fun `resuming puts it back in line rather than straight into a transfer`() {
        val resumed = QueueReducer.reduce(row(QueueState.PAUSED), QueueAction.Resume)

        assertEquals(QueueState.AVAILABLE, resumed.state)
    }

    @Test
    fun `and from there it starts the way every other row does`() {
        val resumed = QueueReducer.reduce(row(QueueState.PAUSED), QueueAction.Resume)
        val running = QueueReducer.reduce(resumed, QueueAction.StartDownload)

        assertEquals(QueueState.DOWNLOADING, running.state)
    }

    @Test
    fun `nothing else can be paused`() {
        listOf(QueueState.AVAILABLE, QueueState.VERIFYING, QueueState.READY_TO_INSTALL).forEach {
            assertThrows(IllegalStateException::class.java) {
                QueueReducer.reduce(row(it), QueueAction.Pause)
            }
        }
    }

    @Test
    fun `and only a paused row can be resumed`() {
        assertThrows(IllegalStateException::class.java) {
            QueueReducer.reduce(row(QueueState.DOWNLOADING), QueueAction.Resume)
        }
    }
}
