package dev.wystore.ui.tv

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.wystore.R
import dev.wystore.data.ManagedSource
import dev.wystore.data.library.LibraryCard
import dev.wystore.data.library.LibraryCatalogPolicy
import dev.wystore.settings.AppSettings
import dev.wystore.settings.TvCatalog
import dev.wystore.ui.theme.WyStoreTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TvLibraryCategoryTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun foreignCategoryOpensLibraryCardsThroughTheirOwnSource() {
        val library = LibraryCatalogPolicy.cardToApp(LibraryCard("example.library", "Library fixture", "Example",
            null, true, emptyMap()), "en_US")
        val rustore = (1..3).map { library.copy(packageName = "example.finance$it", name = "Finance fixture $it",
            source = ManagedSource.RUSTORE, categories = listOf("finance")) }
        var opened: String? = null
        compose.setContent {
            WyStoreTheme(AppSettings(), tv = true) {
                TvHomeScreen(TvCatalogUiState(apps = rustore), TvCatalog.TV,
                    TvPackageContext(emptyList(), emptyList(), emptyList(), emptyList()),
                    remember { FocusRequester() }, true, null,
                    onOpenApp = { error("A library card must not use the RuStore entry route") },
                    onRetry = {}, onOpenMyApps = {}, githubEntries = emptyList(), onOpenGitHub = {},
                    listState = rememberLazyListState(), rowStates = remember { mutableMapOf() },
                    libraryApps = listOf(library), onOpenLibrary = { opened = it })
            }
        }
        compose.onAllNodesWithText(compose.activity.getString(R.string.wy_category_foreign))[0]
            .assertIsDisplayed().performClick()
        compose.onNodeWithText(library.name).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(library.packageName, opened) }
    }
}
