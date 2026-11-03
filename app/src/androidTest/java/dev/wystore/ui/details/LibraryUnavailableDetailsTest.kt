package dev.wystore.ui.details

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.wystore.R
import dev.wystore.data.library.LibraryCard
import dev.wystore.data.library.LibraryCatalogPolicy
import dev.wystore.settings.AppSettings
import dev.wystore.ui.theme.WyStoreTheme
import org.junit.Rule
import org.junit.Test

class LibraryUnavailableDetailsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun unavailableBuildIsNotPresentedAsAnInstallAction() {
        val app = LibraryCatalogPolicy.cardToApp(LibraryCard("example.unsupported", "Example", "Developer",
            null, true, emptyMap()), "en_US").copy(installationAvailable = false,
            installationUnavailableReason = "Compatibility fixture")
        compose.setContent {
            WyStoreTheme(AppSettings()) {
                AppDetailsScreen(app, null, null, false, false, null, false, false, {}, {}, {}, {}, {}, onReplace = {})
            }
        }
        compose.onNodeWithText(compose.activity.getString(R.string.library_install_unavailable))
            .assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("Compatibility fixture").assertIsDisplayed()
    }
}
