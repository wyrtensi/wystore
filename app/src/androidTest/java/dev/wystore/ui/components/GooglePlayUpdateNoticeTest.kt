package dev.wystore.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.wystore.R
import dev.wystore.data.InstallSource
import dev.wystore.data.InstalledApp
import dev.wystore.data.ManagedApp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GooglePlayUpdateNoticeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val installed = InstalledApp("example.app", "Example", "1.0", 1, 0,
        InstallSource.WY_STORE, emptySet(), installerPackageName = "app.wystore")
    private val managed = ManagedApp(installed.packageName, installed.label, emptySet(),
        googlePlayUpdateDetected = true)

    @Test fun settingsActionRemainsAvailableAfterTheGlobalOverrideChanges() {
        val continueChecks = mutableStateOf(false)
        var settingsRequests = 0
        compose.setContent {
            MaterialTheme {
                GooglePlayUpdateNotice(managed, installed, continueChecks.value, { settingsRequests++ })
            }
        }
        val pause = compose.activity.getString(R.string.google_play_pause_reason)
        val continued = compose.activity.getString(R.string.google_play_continue_reason)
        val action = compose.activity.getString(R.string.google_play_update_settings)
        compose.onNodeWithText(pause).assertIsDisplayed()
        compose.onNodeWithText(action).performClick()
        compose.runOnIdle { assertEquals(1, settingsRequests); continueChecks.value = true }
        compose.onNodeWithText(pause).assertDoesNotExist()
        compose.onNodeWithText(continued).assertIsDisplayed()
        compose.onNodeWithText(action).performClick()
        compose.runOnIdle { assertEquals(2, settingsRequests) }
    }

    @Test fun anUnmanagedAppDoesNotClaimItsChecksArePaused() {
        compose.setContent {
            MaterialTheme { GooglePlayUpdateNotice(null, installed, false, {}) }
        }
        compose.onNodeWithText(compose.activity.getString(R.string.google_play_pause_reason)).assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(R.string.google_play_update_settings)).assertDoesNotExist()
    }

    @Test fun anAppWithoutGooglePlayHistoryDoesNotShowTheNotice() {
        compose.setContent {
            MaterialTheme {
                GooglePlayUpdateNotice(managed.copy(googlePlayUpdateDetected = false), installed, false, {})
            }
        }
        compose.onNodeWithText(compose.activity.getString(R.string.google_play_pause_reason)).assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(R.string.google_play_update_settings)).assertDoesNotExist()
    }

    @Test fun perAppPermissionShowsContinuedChecksWithoutTheGlobalOverride() {
        compose.setContent {
            MaterialTheme { GooglePlayUpdateNotice(managed.copy(forceWyStore = true), installed, false, {}) }
        }
        compose.onNodeWithText(compose.activity.getString(R.string.google_play_continue_reason)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.google_play_pause_reason)).assertDoesNotExist()
    }
}
