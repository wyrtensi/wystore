package dev.wystore.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryIconIndexTest {

    private val index = CategoryIconIndex.of(
        listOf(
            StoreCategory(slug = "entertainment", title = "Развлечения", iconUrl = "https://icons/fun.svg"),
            StoreCategory(slug = "tools", title = "Полезные инструменты", iconUrl = null)
        )
    )

    @Test
    fun theTvCatalogueNamesItsCategoryBySlug() {
        // What a TV app's category actually holds, as read from the device.
        assertEquals("https://icons/fun.svg", CategoryIconIndex.lookup(index, "entertainment"))
    }

    @Test
    fun aTitleFindsItTooIgnoringCaseAndSpacing() {
        assertEquals("https://icons/fun.svg", CategoryIconIndex.lookup(index, " развлечения "))
    }

    @Test
    fun aGenreASectionWithoutAnIconOrNothingHasNone() {
        assertNull(CategoryIconIndex.lookup(index, "Головоломки"))
        assertNull(CategoryIconIndex.lookup(index, "tools"))
        assertNull(CategoryIconIndex.lookup(index, null))
    }
}
