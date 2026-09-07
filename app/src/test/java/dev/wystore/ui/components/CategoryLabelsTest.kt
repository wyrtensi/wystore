package dev.wystore.ui.components

import dev.wystore.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryLabelsTest {
    @Test
    fun namesTheSectionsTheCatalogActuallyPublishes() {
        assertEquals(R.string.category_finance, CategoryLabels.stringRes("finance"))
        assertEquals(R.string.category_travelling, CategoryLabels.stringRes("travelling"))
    }

    /** The app JSON spells it "adsAndServices"; the catalogue URL spells it "adsandservices". */
    @Test
    fun matchesTheSameSectionInEitherSpelling() {
        assertEquals(
            CategoryLabels.stringRes("adsandservices"),
            CategoryLabels.stringRes("adsAndServices")
        )
    }

    @Test
    fun hasNoNameForASectionItHasNotSeen() {
        assertNull(CategoryLabels.stringRes("quantumComputing"))
    }

    @Test
    fun turnsAnUnknownSlugIntoWords() {
        assertEquals("Quantum computing", CategoryLabels.humanize("quantumComputing"))
        assertEquals("Books", CategoryLabels.humanize("books"))
        assertEquals("", CategoryLabels.humanize(""))
    }

    /**
     * The same pill carries the GitHub catalogue's own labels, which are already written for
     * people. Split on case, "GitHub" became "Git hub" and "GPL-3.0" became "G p l-3.0".
     */
    @Test
    fun leavesLabelsWrittenForPeopleAlone() {
        assertEquals("GitHub", CategoryLabels.humanize("GitHub"))
        assertEquals("GPL-3.0", CategoryLabels.humanize("GPL-3.0"))
        assertEquals("Обход блокировок", CategoryLabels.humanize("Обход блокировок"))
        assertEquals("VPN", CategoryLabels.humanize("VPN"))
    }
}
