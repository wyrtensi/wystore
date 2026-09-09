package dev.wystore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How "update all" walks its list.
 *
 * Installing needs Android's confirmation dialog one app at a time, so the batch has to know when
 * the current one is finished before offering the next. The first attempt hooked the Activity's
 * install callback, which never fires for session installs — those report to a BroadcastReceiver —
 * so the batch stopped after the first app. It follows the queue instead.
 */
class InstallBatchPolicyTest {

    @Test
    fun anInstallThatIsStillOnScreenIsNotSettled() {
        assertFalse(
            InstallBatchPolicy.isSettled(listOf(InstallQueueStatus.INSTALLING), handedOver = true)
        )
        // Downloaded and waiting for the dialog to appear: the handover has not happened yet.
        assertFalse(
            InstallBatchPolicy.isSettled(listOf(InstallQueueStatus.READY), handedOver = false)
        )
    }

    @Test
    fun anInstallThatEndedOneWayOrAnotherIsSettled() {
        assertTrue(
            InstallBatchPolicy.isSettled(listOf(InstallQueueStatus.COMPLETE), handedOver = true)
        )
        assertTrue(
            InstallBatchPolicy.isSettled(listOf(InstallQueueStatus.FAILED), handedOver = false)
        )
        assertTrue(InstallBatchPolicy.isSettled(emptyList(), handedOver = false))
    }

    @Test
    fun aDeclinedInstallCountsAsAnAnswer() {
        // Declining puts the row back to "downloaded, waiting", which is indistinguishable from the
        // state it was in before the dialog appeared. The handover is what tells them apart.
        assertTrue(
            InstallBatchPolicy.isSettled(listOf(InstallQueueStatus.READY), handedOver = true)
        )
    }

    @Test
    fun theHandoverIsTheMomentAndroidTakesTheApk() {
        assertTrue(InstallBatchPolicy.isHandedOver(listOf(InstallQueueStatus.INSTALLING)))
        assertFalse(InstallBatchPolicy.isHandedOver(listOf(InstallQueueStatus.READY)))
    }

    @Test
    fun theBatchTakesTheNextAppThatIsStillWaitingToBeInstalled() {
        val remaining = listOf("a.pkg", "b.pkg", "c.pkg")

        assertEquals("b.pkg", InstallBatchPolicy.next(remaining, setOf("b.pkg", "c.pkg")))
    }

    @Test
    fun anAppThatIsNoLongerWaitingIsSkippedRatherThanStallingTheBatch() {
        // "a.pkg" was installed from its own card while the batch was running.
        val remaining = listOf("a.pkg", "b.pkg")

        assertEquals("b.pkg", InstallBatchPolicy.next(remaining, setOf("b.pkg")))
        // "a.pkg" is dropped along with it: it is behind the one being taken, not ahead of it.
        assertEquals(emptyList<String>(), InstallBatchPolicy.remainingAfter(remaining, "b.pkg"))
    }

    @Test
    fun nothingLeftToInstallEndsTheBatch() {
        assertNull(InstallBatchPolicy.next(listOf("a.pkg"), emptySet()))
        assertNull(InstallBatchPolicy.next(emptyList(), setOf("a.pkg")))
        assertEquals(emptyList<String>(), InstallBatchPolicy.remainingAfter(listOf("a.pkg"), null))
    }

    /**
     * Adopting a dozen apps at once with "install once downloaded" on produced one confirmation
     * dialog and eleven silent downloads: every finished download after the first was dropped.
     */
    @Test
    fun everyDownloadThatAskedToBeInstalledJoinsTheBatch() {
        val additions = InstallBatchPolicy.autoInstallAdditions(
            requested = setOf("a.pkg", "b.pkg", "c.pkg"),
            pending = listOf("a.pkg", "b.pkg", "c.pkg"),
            alreadyQueued = emptyList(),
            current = null
        )

        assertEquals(listOf("a.pkg", "b.pkg", "c.pkg"), additions)
    }

    /** Consulted on every queue change, so a package already in the batch must not join twice. */
    @Test
    fun anAppTheBatchIsAlreadyCarryingIsNotEnrolledAgain() {
        val additions = InstallBatchPolicy.autoInstallAdditions(
            requested = setOf("a.pkg", "b.pkg", "c.pkg"),
            pending = listOf("a.pkg", "b.pkg", "c.pkg"),
            alreadyQueued = listOf("c.pkg"),
            current = "a.pkg"
        )

        assertEquals(listOf("b.pkg"), additions)
    }

    @Test
    fun aDownloadNobodyAskedToInstallIsLeftAlone() {
        val additions = InstallBatchPolicy.autoInstallAdditions(
            requested = setOf("a.pkg"),
            pending = listOf("a.pkg", "b.pkg"),
            alreadyQueued = emptyList(),
            current = null
        )

        assertEquals(listOf("a.pkg"), additions)
    }

    /** The request outlives the download, so a package still downloading is not offered yet. */
    @Test
    fun anAppThatHasNotFinishedDownloadingIsNotOfferedYet() {
        val additions = InstallBatchPolicy.autoInstallAdditions(
            requested = setOf("a.pkg", "b.pkg"),
            pending = listOf("a.pkg"),
            alreadyQueued = emptyList(),
            current = null
        )

        assertEquals(listOf("a.pkg"), additions)
    }

    @Test
    fun theRestOfTheListSurvivesTheOneBeingInstalled() {
        assertEquals(
            listOf("c.pkg"),
            InstallBatchPolicy.remainingAfter(listOf("a.pkg", "b.pkg", "c.pkg"), "b.pkg")
        )
    }
}
