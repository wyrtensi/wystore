package dev.wystore

import dev.wystore.updates.model.QueueState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * How a durable queue row is read by the screens.
 *
 * Every state has to map to something the UI treats correctly; the one that got this wrong was the
 * outcome of a successful install, which the screens went on reading as work in progress.
 */
class QueueStateMappingTest {

    @Test
    fun aFinishedInstallReadsAsComplete() {
        assertEquals(InstallQueueStatus.COMPLETE, QueueState.INSTALLED.toInstallQueueStatus())
    }

    @Test
    fun aRowLeftBehindByAnOlderVersionAlsoReadsAsComplete() {
        // Installs used to end in OFFER_NEXT, and those rows are still in the database of everyone
        // who updates. They are finished installs, so they must not keep an app page busy.
        assertEquals(InstallQueueStatus.COMPLETE, QueueState.OFFER_NEXT.toInstallQueueStatus())
    }

    @Test
    fun aDownloadedRowWaitingForTheUserReadsAsReady() {
        setOf(
            QueueState.READY_TO_INSTALL,
            QueueState.AWAITING_UNKNOWN_SOURCES_PERMISSION,
            QueueState.AWAITING_USER_CONFIRMATION
        ).forEach { state ->
            assertEquals("$state", InstallQueueStatus.READY, state.toInstallQueueStatus())
        }
    }

    @Test
    fun everyStateMapsToSomethingTheScreensUnderstand() {
        // A missing branch would be a compile error, but this also pins that no state is silently
        // reported as busy: only the ones where the queue really is working.
        val busy = QueueState.entries.filter { it.toInstallQueueStatus().isInFlight }
        assertEquals(
            setOf(
                QueueState.AVAILABLE,
                QueueState.CHECKING,
                QueueState.DOWNLOADING,
                QueueState.VERIFYING,
                QueueState.INSTALLING
            ),
            busy.toSet()
        )
    }
}
