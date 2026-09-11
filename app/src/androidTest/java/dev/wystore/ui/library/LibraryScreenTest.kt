package dev.wystore.ui.library

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.wystore.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryScreenTest {

    @get:Rule
    val composeTestRule = androidx.compose.ui.test.junit4.createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test
    fun libraryScreenRendersSections() {
        composeTestRule.setContent {
            LibraryScreen(
                installed = emptyList(),
                managed = emptyList(),
                pendingUpdates = emptyList(),
                updateCheckTask = null,
                onCheckUpdates = {},
                onUpdateAll = {},
                onAdopt = {},
                onUpdateManaged = {},
                onRequestForce = {},
                onRemoveManaged = {},
                onUninstall = {},
                onCheck = {},
                onOpenDetails = {},
                onOpenStorePage = {},
                onLaunch = {},
                onInstallPending = {}
            )
        }

        // Resolved from resources so the assertion holds whatever locale the test device runs in.
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.library_title))
            .assertIsDisplayed()
    }
}
