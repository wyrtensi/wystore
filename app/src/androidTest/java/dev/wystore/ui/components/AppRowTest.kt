package dev.wystore.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.wystore.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppRowTest {

    @get:Rule
    val composeTestRule = androidx.compose.ui.test.junit4.createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test
    fun appRowDisplaysAndHandlesClicks() {
        var rowClicked = false
        var actionClicked = false

        val state = PackageUiState(
            packageName = "ru.vk.store",
            label = "RuStore",
            versionName = "2.0.0",
            versionCode = 200L,
            status = StatusMessage(StatusCode.READY_TO_INSTALL),
            primaryAction = PrimaryAction.Install
        )

        composeTestRule.setContent {
            AppRow(
                state = state,
                onRowClick = { rowClicked = true },
                onPrimaryActionClick = { actionClicked = true }
            )
        }

        composeTestRule.onNodeWithText("RuStore").assertIsDisplayed()
        // Resolved from resources so the assertion holds whatever locale the test device runs in.
        val installLabel = composeTestRule.activity.getString(R.string.common_install)
        composeTestRule.onNodeWithText(installLabel).assertIsDisplayed().performClick()

        assertTrue("Action should be clicked", actionClicked)
    }
}
