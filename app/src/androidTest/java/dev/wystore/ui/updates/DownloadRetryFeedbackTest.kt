package dev.wystore.ui.updates

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.wystore.InstallQueueItem
import dev.wystore.InstallQueueStatus
import dev.wystore.R
import dev.wystore.updates.QueueErrorStrings
import dev.wystore.updates.model.QueueErrorCode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadRetryFeedbackTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun queuedFailureIsReadableAndDisappearsWhenTheDownloadResumes() {
        val item = mutableStateOf(queueItem(InstallQueueStatus.QUEUED, QueueErrorCode.NETWORK_TIMEOUT))
        compose.setContent {
            MaterialTheme {
                UpdatesScreen(
                    managed = emptyList(), installed = emptyList(), pendingUpdates = emptyList(),
                    updateCheckTask = null, lastUpdateCheck = null, queue = listOf(item.value),
                    onOpen = {}, onInstallPending = {}, onDiscardPending = {}
                )
            }
        }
        val previous = compose.activity.getString(
            R.string.queue_previous_attempt_failed,
            compose.activity.getString(R.string.queue_error_network_timeout)
        )
        compose.onNodeWithText(previous).performScrollTo().assertIsDisplayed()
        compose.runOnIdle { item.value = item.value.copy(status = InstallQueueStatus.DOWNLOADING) }
        compose.onNodeWithText(previous).assertDoesNotExist()
        compose.runOnIdle { item.value = item.value.copy(status = InstallQueueStatus.COMPLETE) }
        compose.onNodeWithText(previous).assertDoesNotExist()
    }

    @Test
    fun terminalNetworkTimeoutExplainsHowToRetryWithoutBlamingTheSource() {
        compose.setContent {
            MaterialTheme {
                UpdatesScreen(
                    managed = emptyList(), installed = emptyList(), pendingUpdates = emptyList(),
                    updateCheckTask = null, lastUpdateCheck = null,
                    queue = listOf(queueItem(InstallQueueStatus.FAILED, QueueErrorCode.NETWORK_TIMEOUT)),
                    onOpen = {}, onInstallPending = {}, onDiscardPending = {}
                )
            }
        }
        compose.onNodeWithText(compose.activity.getString(QueueErrorStrings.stringRes(QueueErrorCode.NETWORK_TIMEOUT)))
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.source_failure_help)).assertDoesNotExist()
    }

    @Test
    fun anInstallConfirmationTimeoutDoesNotBlameTheConnection() {
        compose.setContent {
            MaterialTheme {
                UpdatesScreen(
                    managed = emptyList(), installed = emptyList(), pendingUpdates = emptyList(),
                    updateCheckTask = null, lastUpdateCheck = null,
                    queue = listOf(queueItem(InstallQueueStatus.FAILED, QueueErrorCode.TIMEOUT)),
                    onOpen = {}, onInstallPending = {}, onDiscardPending = {}
                )
            }
        }
        compose.onNodeWithText(compose.activity.getString(R.string.queue_error_timeout))
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.queue_error_network_timeout))
            .assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(R.string.queue_error_network))
            .assertDoesNotExist()
    }

    private fun queueItem(status: InstallQueueStatus, error: QueueErrorCode) = InstallQueueItem(
        id = "download-feedback", packageName = "app.wystore", label = "Wy Store",
        status = status, versionName = "0.6.3", errorCode = error
    )
}
