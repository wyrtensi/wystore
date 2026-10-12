package dev.wystore.updates

import dev.wystore.updates.model.QueueState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutdatedDownloadPolicyTest {

    private fun outdated(
        state: QueueState = QueueState.READY_TO_INSTALL,
        rowVersion: Long = 2724,
        installed: Long? = 2724,
        userRequested: Boolean = false
    ) = OutdatedDownloadPolicy.isOutdated(state.name, rowVersion, installed, userRequested)

    @Test
    fun aDownloadAnotherStoreAlreadyInstalledIsOutdated() {
        // Google Play updated OZON to the same version, and Wy Store went on offering it as ready.
        assertTrue(outdated())
        assertTrue(outdated(rowVersion = 2721))
    }

    @Test
    fun aNewerDownloadStays() {
        assertFalse(outdated(rowVersion = 2725))
    }

    @Test
    fun theSameVersionAskedForByHandStays() {
        // "Hand updates to Wy Store" fetches the installed version on purpose, so that Wy Store
        // becomes its installer.
        assertFalse(outdated(userRequested = true))
        assertTrue(outdated(rowVersion = 2700, userRequested = true))
    }

    @Test
    fun onlyDownloadsWaitingToBeInstalledAreJudged() {
        assertFalse(outdated(state = QueueState.INSTALLING))
        assertFalse(outdated(state = QueueState.AWAITING_USER_CONFIRMATION))
        assertFalse(outdated(state = QueueState.DOWNLOADING))
    }

    @Test
    fun anAppThatIsNotInstalledHasNothingToCompareWith() {
        assertFalse(outdated(installed = null))
    }
}
