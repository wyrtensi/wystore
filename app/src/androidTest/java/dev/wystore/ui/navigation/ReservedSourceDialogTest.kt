package dev.wystore.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.view.KeyEvent
import dev.wystore.R
import dev.wystore.data.ReservedSourcePrompt
import dev.wystore.data.ReservedSourceTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReservedSourceDialogTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun backupFailureExplainsFailureAndRoutesToSources() {
        var target: ReservedSourceTarget? = null
        compose.setContent {
            MaterialTheme {
                ReservedSourceDialog(ReservedSourcePrompt(ReservedSourceTarget.SOURCES, true),
                    onDismiss = {}, onOpen = { target = it })
            }
        }
        compose.onNodeWithText(compose.activity.getString(R.string.reserved_source_backup_failed)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.reserved_source_open_sources)).performClick()
        compose.runOnIdle { assertEquals(ReservedSourceTarget.SOURCES, target) }
    }

    @Test fun closePreservesContextAndDialogCanReopen() {
        var visible by mutableStateOf(true)
        var opened = false
        compose.setContent {
            MaterialTheme {
                if (visible) ReservedSourceDialog(ReservedSourcePrompt(ReservedSourceTarget.ABOUT, false),
                    onDismiss = { visible = false }, onOpen = { opened = true })
            }
        }
        val close = compose.activity.getString(R.string.common_close)
        compose.onNodeWithText(close).performClick()
        compose.runOnIdle { assertFalse(opened); visible = true }
        compose.onNodeWithText(compose.activity.getString(R.string.reserved_source_open_about)).assertIsDisplayed()
    }

    @Test fun remoteFocusStartsOnTheAction() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.setContent {
            MaterialTheme {
                ReservedSourceDialog(ReservedSourcePrompt(ReservedSourceTarget.ABOUT, false), {}, {})
            }
        }
        compose.onNodeWithText(compose.activity.getString(R.string.reserved_source_open_about)).assertIsFocused()
    }
}
