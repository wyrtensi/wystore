package dev.wystore.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class RowActionPolicyTest {

    @Test
    fun installOnAFreshAppQueuesIt() {
        // The reported bug: this used to open the app's page instead.
        assertEquals(
            RowAction.Enqueue,
            RowActionPolicy.actionFor(PrimaryAction.Install, StatusCode.NOT_INSTALLED)
        )
    }

    @Test
    fun installOnAFinishedDownloadGoesStraightToAndroid() {
        assertEquals(
            RowAction.InstallDownloaded,
            RowActionPolicy.actionFor(PrimaryAction.Install, StatusCode.READY_TO_INSTALL)
        )
    }

    @Test
    fun anInstalledAppOpens() {
        assertEquals(
            RowAction.OpenApp,
            RowActionPolicy.actionFor(PrimaryAction.Open, StatusCode.INSTALLED)
        )
    }

    @Test
    fun updateResumeAndRetryAllGoThroughTheQueue() {
        assertEquals(
            RowAction.Enqueue,
            RowActionPolicy.actionFor(PrimaryAction.Update, StatusCode.INSTALLED)
        )
        assertEquals(
            RowAction.Enqueue,
            RowActionPolicy.actionFor(PrimaryAction.Resume, StatusCode.CANCELLED)
        )
        assertEquals(
            RowAction.Enqueue,
            RowActionPolicy.actionFor(PrimaryAction.Retry, StatusCode.FAILED_NETWORK)
        )
    }

    @Test
    fun aTransferInFlightIsLeftAlone() {
        assertEquals(
            RowAction.Nothing,
            RowActionPolicy.actionFor(PrimaryAction.Pause, StatusCode.DOWNLOADING)
        )
        assertEquals(
            RowAction.Nothing,
            RowActionPolicy.actionFor(PrimaryAction.Installing, StatusCode.INSTALLING)
        )
        assertEquals(
            RowAction.Nothing,
            RowActionPolicy.actionFor(PrimaryAction.None, StatusCode.INSTALLED)
        )
    }
}
