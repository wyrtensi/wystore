package dev.wystore.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.wystore.R
import dev.wystore.data.RuStoreCompatibility
import dev.wystore.data.StoreSettings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = androidx.compose.ui.test.junit4.createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test
    fun settingsScreenRendersHubOptions() {
        composeTestRule.setContent {
            SettingsScreen(
                settings = StoreSettings(),
                ruStoreCompatibility = RuStoreCompatibility(),
                ruStoreCompatibilityTask = null,
                rootAvailable = true,
                managedCount = 5,
                githubCount = 2,
                onSave = {},
                onCheckRoot = {},
                onCheckRuStore = {},
                onSetRuStoreVersionCode = {},
                onExportUri = {},
                onImportUri = { _, _ -> },
                onExportJson = { "{}" },
                onRestoreJson = { _, _ -> }
            )
        }

        composeTestRule.onNodeWithText(string(R.string.settings_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.settings_hub_permissions_title)).assertIsDisplayed()
    }

    /** Resolved from resources so assertions hold whatever locale the test device runs in. */
    private fun string(id: Int): String = composeTestRule.activity.getString(id)
}
