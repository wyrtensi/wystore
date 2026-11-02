package dev.wystore.ui.components

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import dev.wystore.data.AppSourceChoice
import dev.wystore.data.GitHubRepository
import dev.wystore.data.ManagedSource
import dev.wystore.settings.AppSettings
import dev.wystore.ui.theme.WyStoreTheme
import dev.wystore.ui.tv.TvSourceSelector
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AppSourceSelectorTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val choices = listOf(AppSourceChoice.RuStore("example.app"),
        AppSourceChoice.GitHub(GitHubRepository("example", "app"), "example.app"), AppSourceChoice.Library("example.app"))

    @Test fun compactSelectorWrapsAllSourcesAndOnlyChoosesOnExplicitClick() {
        var chosen: AppSourceChoice? = null
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 1.15f)) {
                MaterialTheme {
                    Box(Modifier.width(250.dp)) {
                        AppSourceSelector(choices, ManagedSource.RUSTORE, ManagedSource.RUSTORE, { chosen = it })
                    }
                }
            }
        }
        compose.runOnIdle { assertNull(chosen) }
        listOf("RuStore", "GitHub", "Wy Store").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
            val bounds = compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.left >= 0 && bounds.right <= 250 * compose.activity.resources.displayMetrics.density + 1)
        }
        compose.onNodeWithText("Wy Store").performClick()
        compose.runOnIdle { assertEquals(choices.last(), chosen) }
    }

    @Test fun tvRemoteCanMoveBetweenSourcesAndConfirmAChoice() {
        var chosen: AppSourceChoice? = null
        compose.setContent {
            WyStoreTheme(AppSettings(), tv = true) {
                TvSourceSelector(choices, ManagedSource.RUSTORE, ManagedSource.RUSTORE, { chosen = it })
            }
        }
        compose.onNodeWithText("RuStore").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onNodeWithText("GitHub").assertIsFocused()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.runOnIdle { assertEquals(choices[1], chosen) }
    }
}
