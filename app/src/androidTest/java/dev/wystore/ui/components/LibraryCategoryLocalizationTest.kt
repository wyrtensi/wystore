package dev.wystore.ui.components

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.wystore.R
import dev.wystore.data.SearchSources
import dev.wystore.data.library.LibraryCard
import dev.wystore.data.library.LibraryCatalogPolicy
import dev.wystore.settings.AppSettings
import dev.wystore.ui.search.SearchScreen
import dev.wystore.ui.theme.WyStoreTheme
import java.util.Locale
import org.junit.Rule
import org.junit.Test

class LibraryCategoryLocalizationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun searchUsesTheAppCategoryInAllNineLanguages() {
        val language = mutableStateOf("ru")
        val app = LibraryCatalogPolicy.cardToApp(LibraryCard("example.browser", "Browser", "Developer", null,
            true, emptyMap(), listOf("browsers")), "en_US")
        compose.setContent {
            val original = LocalContext.current
            val configuration = Configuration(LocalConfiguration.current).apply { setLocale(Locale.forLanguageTag(language.value)) }
            val context = original.createConfigurationContext(configuration)
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides configuration) {
                WyStoreTheme(AppSettings()) {
                    SearchScreen(query = "browser", apps = listOf(app), total = 1, busy = false, operation = null,
                        installed = emptyList(), queue = emptyList(), pendingUpdates = emptyList(),
                        sources = SearchSources.WY_STORE, githubResults = emptyList(), onOpenGitHubApp = {},
                        onSearchLocal = {}, onSearch = {}, onOpen = {}, onQuickInstall = {}, onLaunch = {})
                }
            }
        }
        for (tag in listOf("ru", "en", "be", "uk", "kk", "lv", "uz", "sr", "zh")) {
            compose.runOnIdle { language.value = tag }
            val conf = Configuration(compose.activity.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
            val localized = compose.activity.createConfigurationContext(conf)
            compose.onNodeWithText(localized.getString(R.string.category_browsers)).assertExists()
            if (tag == "ru") compose.onNodeWithText(localized.getString(R.string.wy_category_foreign)).assertDoesNotExist()
        }
    }
}
