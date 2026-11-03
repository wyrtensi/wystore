package dev.wystore.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.wystore.R
import dev.wystore.data.StoreSettings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReservedSourceEntryTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun libraryActionOpensSources() {
        showSettings(SettingsEntry.SOURCES)
        compose.onNodeWithText(compose.activity.getString(R.string.settings_hub_sources_title)).assertIsDisplayed()
    }

    @Test fun selfUpdateActionOpensAbout() {
        showSettings(SettingsEntry.ABOUT)
        compose.onNodeWithText(compose.activity.getString(R.string.settings_hub_about_title)).assertIsDisplayed()
    }

    private fun showSettings(entry: SettingsEntry) {
        compose.setContent {
            MaterialTheme {
                SettingsScreen(settings = StoreSettings(), rootAvailable = false, managedCount = 0,
                    githubCount = 0, onSave = {}, onCheckRoot = {}, onExportUri = {},
                    onImportUri = { _, _ -> }, onExportJson = { "" }, onRestoreJson = { _, _ -> },
                    initialEntry = entry)
            }
        }
    }
}
