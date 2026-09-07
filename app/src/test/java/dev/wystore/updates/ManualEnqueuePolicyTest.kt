package dev.wystore.updates

import dev.wystore.updates.model.QueueState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What a tap on Install or Update has to do with the row the queue already holds for that package.
 *
 * A row that stopped is not a row a download can start from: the reducer only accepts StartDownload
 * from AVAILABLE or CHECKING. Manual installs used to enqueue on top of whatever state was there,
 * so the worker's first move threw "Action is not allowed while queue item ... is FAILED", which
 * the classifier reported to the user as a network error. Once an app had failed once, it could
 * never be installed again from its card.
 */
class ManualEnqueuePolicyTest {

    @Test
    fun aPackageWithNoRowAtAllJustStarts() {
        assertEquals(ManualEnqueueAction.START, ManualEnqueuePolicy.decide(emptyList()))
    }

    @Test
    fun aStoppedRowIsResetBeforeStarting() {
        setOf(
            QueueState.FAILED,
            QueueState.CANCELED,
            QueueState.SKIPPED,
            QueueState.INSTALLED,
            QueueState.OFFER_NEXT
        ).forEach { state ->
            assertEquals("$state", ManualEnqueueAction.RESET_AND_START, ManualEnqueuePolicy.decide(listOf(state)))
        }
    }

    @Test
    fun aRowThatIsAlreadyRunningIsLeftAlone() {
        setOf(
            QueueState.CHECKING,
            QueueState.DOWNLOADING,
            QueueState.VERIFYING,
            QueueState.INSTALLING
        ).forEach { state ->
            assertEquals("$state", ManualEnqueueAction.IGNORE, ManualEnqueuePolicy.decide(listOf(state)))
        }
    }

    @Test
    fun anAlreadyDownloadedRowIsNotDownloadedAgain() {
        // The artifact is on disk and verified; the step the user needs is the install, not another
        // 200 MB transfer.
        setOf(
            QueueState.READY_TO_INSTALL,
            QueueState.AWAITING_UNKNOWN_SOURCES_PERMISSION,
            QueueState.AWAITING_USER_CONFIRMATION
        ).forEach { state ->
            assertEquals("$state", ManualEnqueueAction.IGNORE, ManualEnqueuePolicy.decide(listOf(state)))
        }
    }

    @Test
    fun anAvailableRowStartsWithoutAReset() {
        assertEquals(ManualEnqueueAction.START, ManualEnqueuePolicy.decide(listOf(QueueState.AVAILABLE)))
    }

    @Test
    fun oneRunningRowSpeaksForThePackageEvenNextToAFailedOne() {
        // A package can hold several rows: one per version the source offered.
        assertEquals(
            ManualEnqueueAction.IGNORE,
            ManualEnqueuePolicy.decide(listOf(QueueState.FAILED, QueueState.DOWNLOADING))
        )
    }
}
