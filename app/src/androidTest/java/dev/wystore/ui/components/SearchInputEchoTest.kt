package dev.wystore.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.wystore.data.SearchSources
import dev.wystore.ui.search.SearchScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SearchInputEchoTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun anEarlierLocalSearchEchoCannotEraseMoreRecentlyTypedCharacters() {
        val echoedQuery = mutableStateOf("")
        val requests = mutableListOf<String>()
        compose.setContent {
            MaterialTheme {
                SearchScreen(query = echoedQuery.value, apps = emptyList(), total = null, busy = false, operation = null,
                    installed = emptyList(), queue = emptyList(), pendingUpdates = emptyList(),
                    sources = SearchSources.WY_STORE, githubResults = emptyList(), onOpenGitHubApp = {},
                    onSearchLocal = { requests += it }, onSearch = {}, onOpen = {}, onQuickInstall = {}, onLaunch = {})
            }
        }
        val field = compose.onNode(hasSetTextAction())
        field.performTextInput("G")
        compose.waitForIdle()
        field.performTextInput("r")
        compose.waitForIdle()
        compose.runOnIdle { echoedQuery.value = "G" }
        field.performTextInput("ok")
        field.assertTextEquals("Grok")
        compose.runOnIdle { assertEquals("Grok", requests.last()) }
    }
}
