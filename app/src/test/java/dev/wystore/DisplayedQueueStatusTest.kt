package dev.wystore

import dev.wystore.updates.model.QueueState
import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayedQueueStatusTest {

    @Test
    fun aRowBeingHandedToAndroidReadsAsInstalling() {
        // The installer session is written in this state, which takes seconds for a large app, and
        // every card offered Install again the whole time.
        assertEquals(
            InstallQueueStatus.INSTALLING,
            QueueState.AWAITING_USER_CONFIRMATION.toDisplayedStatus("ru.app", startingInstall = null)
        )
    }

    @Test
    fun theDownloadAnInstallWasJustStartedForReadsAsInstalling() {
        // Between the tap and the handover the archive is verified again, still READY_TO_INSTALL.
        assertEquals(
            InstallQueueStatus.INSTALLING,
            QueueState.READY_TO_INSTALL.toDisplayedStatus("ru.app", startingInstall = "ru.app")
        )
    }

    @Test
    fun otherDownloadsStillOfferInstall() {
        assertEquals(
            InstallQueueStatus.READY,
            QueueState.READY_TO_INSTALL.toDisplayedStatus("ru.other", startingInstall = "ru.app")
        )
        assertEquals(
            InstallQueueStatus.READY,
            QueueState.READY_TO_INSTALL.toDisplayedStatus("ru.app", startingInstall = null)
        )
    }

    @Test
    fun waitingForTheUnknownSourcesPermissionIsNotInstalling() {
        // Nothing happens until the user grants it, so the card keeps its Install button.
        assertEquals(
            InstallQueueStatus.READY,
            QueueState.AWAITING_UNKNOWN_SOURCES_PERMISSION.toDisplayedStatus("ru.app", startingInstall = "ru.app")
        )
    }

    @Test
    fun everyOtherStateReadsAsTheQueueSaysIt() {
        QueueState.entries
            .filter { it != QueueState.AWAITING_USER_CONFIRMATION && it != QueueState.READY_TO_INSTALL }
            .forEach { state ->
                assertEquals(state.toInstallQueueStatus(), state.toDisplayedStatus("ru.app", startingInstall = "ru.app"))
            }
    }
}
