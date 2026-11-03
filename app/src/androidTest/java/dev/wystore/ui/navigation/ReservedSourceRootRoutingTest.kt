package dev.wystore.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import dev.wystore.R
import dev.wystore.StoreViewModel
import dev.wystore.settings.AppSettings
import dev.wystore.ui.theme.WyStoreTheme
import dev.wystore.ui.tv.TvApp
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ReservedSourceRootRoutingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun phoneLibraryWarningOpensSourceSettings() = checkRoute(false, true)
    @Test fun phoneSelfWarningOpensBuiltInUpdates() = checkRoute(false, false)
    @Test fun tvLibraryWarningOpensSourceSettings() = checkRoute(true, true)
    @Test fun tvSelfWarningOpensBuiltInUpdates() = checkRoute(true, false)

    private fun checkRoute(tv: Boolean, library: Boolean) {
        lateinit var viewModel: StoreViewModel
        compose.runOnIdle {
            viewModel = ViewModelProvider(compose.activity)[StoreViewModel::class.java]
        }
        compose.setContent {
            WyStoreTheme(AppSettings()) {
                if (tv) TvApp(viewModel, {}) else WyStoreRoot(viewModel, {})
            }
        }
        compose.runOnIdle { viewModel.loadGitHubRepository(if (library) "wyrtensi/wystore-library" else "wyrtensi/wystore") }
        val title = compose.activity.getString(R.string.reserved_source_title)
        compose.waitUntilAtLeastOneExists(hasText(title))
        compose.onNodeWithText(compose.activity.getString(if (library) R.string.reserved_source_open_sources
            else R.string.reserved_source_open_about)).performClick()
        compose.onNodeWithText(compose.activity.getString(if (library) R.string.settings_hub_sources_title
            else R.string.settings_hub_about_title)).assertIsDisplayed()
        compose.onNodeWithText(title).assertDoesNotExist()
    }
}
