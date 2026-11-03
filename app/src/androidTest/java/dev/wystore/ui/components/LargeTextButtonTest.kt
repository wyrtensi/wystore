package dev.wystore.ui.components

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
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LargeTextButtonTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun aLongActionRemainsCompleteWithLargeSystemTextOnANarrowScreen() {
        val label = "Проверить и установить обновления"
        var clicked = false
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, 1.8f)) {
                MaterialTheme {
                    Box(Modifier.width(240.dp)) {
                        HubWideButton(label, onClick = { clicked = true })
                    }
                }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(label, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
            assertTrue(it(layouts))
        }
        assertTrue(layouts.isNotEmpty())
        assertFalse("the action label must not be clipped or ellipsized", layouts.single().hasVisualOverflow)
        compose.onNodeWithText(label).performClick()
        compose.runOnIdle { assertTrue(clicked) }
    }
}
