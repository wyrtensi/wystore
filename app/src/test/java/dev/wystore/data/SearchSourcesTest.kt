package dev.wystore.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchSourcesTest {

    @Test
    fun withGitHubSwitchedOffEveryChoiceSearchesRuStore() {
        // A saved "GitHub only" used to search nowhere once the source was switched off.
        SearchSources.entries.forEach { assertEquals(SearchSources.RUSTORE, it.within(githubEnabled = false)) }
    }

    @Test
    fun withGitHubOnTheChoiceStands() {
        SearchSources.entries.forEach { assertEquals(it, it.within(githubEnabled = true)) }
    }
}
