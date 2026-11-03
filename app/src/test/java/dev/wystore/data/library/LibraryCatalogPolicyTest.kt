package dev.wystore.data.library

import dev.wystore.data.AppSourceIdentity
import dev.wystore.data.ManagedSource
import dev.wystore.data.SearchSources
import org.junit.Assert.*
import org.junit.Test

class LibraryCatalogPolicyTest {
    private fun catalog() = LibraryJson.parseCatalog(javaClass.getResource("/library/catalog.json")!!.readText())
    @Test fun `app categories come from the catalog independently of foreign shelf membership`() {
        val json = javaClass.getResource("/library/catalog.json")!!.readText()
        val root = com.google.gson.JsonParser.parseString(json).asJsonObject
        root.getAsJsonArray("apps")[0].asJsonObject.add("categories", com.google.gson.JsonArray().apply { add("browsers") })
        val card = LibraryJson.parseCatalog(root.toString()).cards.single()
        assertEquals(listOf("browsers"), LibraryCatalogPolicy.cardToApp(card, "en_US").categories)
        assertEquals(listOf("browsers"), LibraryCatalogPolicy.cardToApp(card.copy(foreignCategory = false), "ru_RU").categories)
    }
    @Test fun `an older catalog never labels an app category as foreign apps`() {
        assertTrue(LibraryCatalogPolicy.cardToApp(catalog().cards.single(), "en_US").categories.isEmpty())
    }
    @Test fun `editorial exclusion changes the category but not search`() {
        val card = catalog().cards.single().copy(foreignCategory = false)
        val data = LibraryCatalog(listOf(card))
        assertTrue(LibraryCatalogPolicy.foreign(data).isEmpty())
        assertEquals(listOf(card), LibraryCatalogPolicy.search(data, "org.example.library"))
        assertEquals(listOf(card), LibraryCatalogPolicy.search(data, "english summary"))
    }
    @Test fun `same package merges badges without changing the primary source`() {
        val library = LibraryCatalogPolicy.cardToApp(catalog().cards.single(), "en_US")
        val rustore = library.copy(source = ManagedSource.RUSTORE, availableSources = setOf(ManagedSource.RUSTORE), appId = 42)
        val merged = AppSourceIdentity.merge(listOf(rustore, library)).single()
        assertEquals(ManagedSource.RUSTORE, merged.source)
        assertEquals(setOf(ManagedSource.RUSTORE, ManagedSource.WY_STORE), merged.availableSources)
    }
    @Test fun `library filtering is independent of the GitHub switch`() {
        assertEquals(SearchSources.WY_STORE, SearchSources.WY_STORE.within(false, true))
        assertEquals(SearchSources.RUSTORE, SearchSources.WY_STORE.within(true, false))
        assertFalse(SearchSources.WY_STORE.includesRuStore)
        assertFalse(SearchSources.WY_STORE.includesGitHub)
        assertTrue(SearchSources.WY_STORE.includesLibrary)
    }
}
