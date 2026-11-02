package dev.wystore.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.wystore.data.SearchSources
import dev.wystore.data.library.LibraryCard
import dev.wystore.data.library.LibraryCatalogPolicy
import dev.wystore.ui.search.SearchScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class StickySearchFieldTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun theSearchFieldStaysEditableAfterScrollingThroughResults() {
        val apps = (1..40).map { index -> LibraryCatalogPolicy.cardToApp(
            LibraryCard("example.app$index", "App $index", "Description", null, false, emptyMap()), "en_US") }
        var latestQuery = "apps"
        compose.setContent {
            MaterialTheme {
                SearchScreen(query = "apps", apps = apps, total = apps.size, busy = false, operation = null,
                    installed = emptyList(), queue = emptyList(), pendingUpdates = emptyList(),
                    sources = SearchSources.WY_STORE, githubResults = emptyList(), onOpenGitHubApp = {},
                    onSearchLocal = { latestQuery = it }, onSearch = {}, onOpen = {}, onQuickInstall = {}, onLaunch = {})
            }
        }
        compose.onNode(hasScrollAction()).performScrollToIndex(30)
        compose.onNode(hasSetTextAction()).assertIsDisplayed().performTextClearance()
        compose.onNode(hasSetTextAction()).performTextInput("planfix")
        compose.onNode(hasSetTextAction()).assertTextEquals("planfix")
        compose.runOnIdle { assertEquals("planfix", latestQuery) }
    }
}
