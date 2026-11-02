package dev.wystore.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.wystore.data.ManagedSource
import dev.wystore.data.library.LibraryCard
import dev.wystore.data.library.LibraryCatalogPolicy
import dev.wystore.ui.search.SearchCard
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SearchSourceBadgesTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun mergedResultShowsTwoSourcesWithoutRatingsOrCategories() = show(setOf(ManagedSource.RUSTORE, ManagedSource.WY_STORE))
    @Test fun compactMergedResultWrapsAllThreeSources() = show(ManagedSource.entries.toSet())

    private fun show(sources: Set<ManagedSource>) {
        val app = LibraryCatalogPolicy.cardToApp(LibraryCard("example.merged", "Merged fixture", "", null, false, emptyMap()), "en_US")
            .copy(source = ManagedSource.RUSTORE, availableSources = sources)
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.15f)) {
                MaterialTheme {
                    Box(Modifier.width(250.dp)) {
                        SearchCard(app, installed = null, queueItem = null, pendingUpdate = null,
                            onOpen = {}, onQuickInstall = {}, onInstallPending = {}, onConfirmUnverifiedSource = {}, onLaunch = {})
                    }
                }
            }
        }
        for (source in sources) {
            val text = when (source) { ManagedSource.RUSTORE -> "RuStore"; ManagedSource.GITHUB -> "GitHub"; ManagedSource.WY_STORE -> "Wy Store" }
            compose.onNodeWithText(text).assertIsDisplayed()
            val bounds = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.left >= 0 && bounds.right <= 250 * compose.activity.resources.displayMetrics.density + 1)
        }
    }
}
