package dev.wystore.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class WyStoreDestinationTest {

    @Test
    fun destinationRoutesMatchSpec() {
        assertEquals("home", WyStoreDestination.Home.route)
        assertEquals("search", WyStoreDestination.Search.route)
        assertEquals("updates", WyStoreDestination.Updates.route)
        assertEquals("library", WyStoreDestination.Library.route)
        assertEquals("settings", WyStoreDestination.Settings.route)
        assertEquals("github", WyStoreDestination.GitHub.route)
        assertEquals("app/ru.vk.store", WyStoreDestination.AppDetails("ru.vk.store").route)
        assertEquals("app/{packageName}", WyStoreDestination.AppDetails.ROUTE_PATTERN)
    }
}
