package dev.wystore.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.view.KeyEvent
import dev.wystore.R
import dev.wystore.data.StoreSettings
import dev.wystore.ui.components.LocalBottomBarInset
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GooglePlaySettingsEntryTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun pauseRecoveryOpensAndRevealsTheSettingWithoutManualScrolling() {
        var saved: StoreSettings? = null
        showSettings { saved = it }
        val title = compose.activity.getString(R.string.settings_google_play_checks_title)
        val label = compose.activity.getString(R.string.settings_google_play_checks)
        compose.onNodeWithText(title).assertIsDisplayed()
        compose.onNodeWithText(label).assertIsDisplayed().performClick()
        val rootBottom = compose.onRoot().fetchSemanticsNode().boundsInRoot.bottom
        val labelBottom = compose.onNodeWithText(label).fetchSemanticsNode().boundsInRoot.bottom
        val navigationHeight = 96 * compose.activity.resources.displayMetrics.density
        assertTrue("The switch label must stay above the floating navigation", labelBottom <= rootBottom - navigationHeight)
        compose.runOnIdle { assertTrue(saved?.continueChecksAfterGooglePlayUpdate == true) }
    }

    @Test
    fun keyboardRecoveryFocusesTheContinueChecksSwitch() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        showSettings {}
        val label = compose.activity.getString(R.string.settings_google_play_checks)
        compose.onNodeWithText(label).assertIsDisplayed().assertIsFocused()
    }

    private fun showSettings(onSave: (StoreSettings) -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalBottomBarInset provides 96.dp) {
              MaterialTheme {
                SettingsScreen(
                    settings = StoreSettings(), rootAvailable = false, managedCount = 1, githubCount = 1,
                    onSave = onSave, onCheckRoot = {}, onExportUri = {}, onImportUri = { _, _ -> },
                    onExportJson = { "" }, onRestoreJson = { _, _ -> }, openGooglePlayUpdates = true
                )
              }
            }
        }
    }
}
