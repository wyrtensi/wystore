package dev.wystore

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What "the queue is busy with this app" means for the UI.
 *
 * Screens used to spell this out as "anything that is not COMPLETE or FAILED". That reading also
 * caught states in which nothing is happening at all, so an app page could show a disabled "Queued"
 * button, and a spinner telling the user not to close the app, for an app that was already
 * installed or whose download had been canceled.
 */
class InstallQueueStatusTest {

    @Test
    fun theQueueIsBusyWhileItIsWorking() {
        val working = setOf(
            InstallQueueStatus.RESOLVING,
            InstallQueueStatus.QUEUED,
            InstallQueueStatus.DOWNLOADING,
            InstallQueueStatus.VERIFYING,
            InstallQueueStatus.INSTALLING
        )
        working.forEach { status ->
            assertTrue("$status is work in progress", status.isInFlight)
        }
    }

    @Test
    fun anInstalledAppIsNotBusy() {
        assertFalse(InstallQueueStatus.COMPLETE.isInFlight)
    }

    @Test
    fun aCanceledItemIsNotBusy() {
        assertFalse(InstallQueueStatus.CANCELED.isInFlight)
    }

    @Test
    fun aFailedItemIsNotBusy() {
        assertFalse(InstallQueueStatus.FAILED.isInFlight)
    }

    @Test
    fun aDownloadedItemWaitingForTheUserIsNotBusy() {
        // Nothing is running: the artifact is on disk and the button must offer Install.
        assertFalse(InstallQueueStatus.READY.isInFlight)
    }

    @Test
    fun aDownloadedItemStillOccupiesTheQueue() {
        // ... but a second download must not start behind its back.
        assertTrue(InstallQueueStatus.READY.occupiesQueue)
    }

    @Test
    fun aCanceledItemReleasesTheQueue() {
        // A canceled row used to make quick install silently do nothing, for good.
        assertFalse(InstallQueueStatus.CANCELED.occupiesQueue)
        assertFalse(InstallQueueStatus.COMPLETE.occupiesQueue)
        assertFalse(InstallQueueStatus.FAILED.occupiesQueue)
    }
}
