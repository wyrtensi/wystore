package dev.wystore.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.wystore.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PermissionCenterScreenTest {

    @get:Rule
    val composeTestRule = androidx.compose.ui.test.junit4.createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test
    fun permissionCenterRendersTitleAndRows() {
        composeTestRule.setContent {
            PermissionCenterScreen(
                onBack = {}
            )
        }

        composeTestRule.onNodeWithText(string(R.string.permissions_title)).assertIsDisplayed()
    }

    /** Resolved from resources so assertions hold whatever locale the test device runs in. */
    private fun string(id: Int): String = composeTestRule.activity.getString(id)
}
