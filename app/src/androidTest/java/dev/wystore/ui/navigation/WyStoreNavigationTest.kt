package dev.wystore.ui.navigation

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WyStoreNavigationTest {

    @Test
    fun destinationRoutesAreCorrect() {
        assertEquals("home", WyStoreDestination.Home.route)
        assertEquals("search", WyStoreDestination.Search.route)
        assertEquals("updates", WyStoreDestination.Updates.route)
        assertEquals("library", WyStoreDestination.Library.route)
        assertEquals("settings", WyStoreDestination.Settings.route)
        assertEquals("app/ru.vk.store", WyStoreDestination.AppDetails("ru.vk.store").route)
    }

    @Test
    fun rootCanResolveStartDestination() {
        assertNotNull(WyStoreDestination.Home)
    }
}
