package dev.wystore.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs against markup captured verbatim from rustore.ru rather than hand-written snippets, so a
 * change in the source's real page structure fails here instead of on the phone.
 */
class RustoreCatalogParserTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name")) {
            "Missing fixture: $name"
        }.bufferedReader().use { it.readText() }

    @Test
    fun categoriesComeFromTheRealCatalogNav() {
        val categories = RustoreHtmlParser.parseCategories(fixture("rustore_catalog_landing.html"))

        assertTrue("expected the full category nav, got ${categories.size}", categories.size >= 15)
        val bySlug = categories.associateBy { it.slug }
        assertEquals("Полезные инструменты", bySlug.getValue("tools").title)
        assertEquals("Финансы", bySlug.getValue("finance").title)
        assertNotNull("the all-apps entry must be browsable too", bySlug["all"])
        assertTrue(
            "category icons must come from a trusted host",
            categories.mapNotNull { it.iconUrl }.all { RustoreUrlPolicy.isTrustedMedia(it) }
        )
        assertEquals("slugs must be unique", categories.size, categories.map { it.slug }.distinct().size)
    }

    @Test
    fun categoriesRejectMarkupThatNoLongerHasTheNav() {
        assertThrows(SourceFormatException::class.java) {
            RustoreHtmlParser.parseCategories("<html><body><p>nothing here</p></body></html>")
        }
    }

    @Test
    fun categoryPageYieldsAppsAndItsLastPage() {
        val page = RustoreHtmlParser.parseCatalogPage(fixture("rustore_catalog_category.html"), "tools", 1)

        assertEquals(5, page.apps.size)
        assertEquals("com.yandex.browser", page.apps.first().packageName)
        assertEquals("Яндекс Браузер с Алисой AI", page.apps.first().name)
        assertEquals(1, page.page)
        assertTrue("the pager advertises more pages", page.hasMore)
        assertTrue("last page should come from the pager", (page.lastPage ?: 0) > 1)
    }

    @Test
    fun landingPageHasNoPagerAndThereforeNoNextPage() {
        val page = RustoreHtmlParser.parseCatalogPage(fixture("rustore_catalog_landing.html"), "", 1)

        assertEquals(5, page.apps.size)
        assertNull(page.lastPage)
        assertFalse(page.hasMore)
    }

    @Test
    fun catalogAndSearchShareTheSameCardShape() {
        val searchPage = RustoreHtmlParser.parseSearchPage(fixture("rustore_search.html"), 1)
        val catalogPage = RustoreHtmlParser.parseCatalogPage(fixture("rustore_catalog_category.html"), "tools", 1)

        assertEquals("org.telegram.messenger.web", searchPage.apps.first().packageName)
        assertEquals(499, searchPage.total)
        // Both routes must fill the same listing fields, otherwise cards render differently.
        listOf(searchPage.apps, catalogPage.apps).forEach { apps ->
            assertTrue(apps.all { it.packageName.isNotBlank() })
            assertTrue(apps.all { it.name.isNotBlank() })
            assertTrue(apps.all { it.versionCode == 0L })
        }
    }

    @Test
    fun aQueryWithNoMatchesIsAnEmptyResultRatherThanAnError() {
        // Captured from a real "нет результатов" search screen, not hand-written.
        val page = RustoreHtmlParser.parseSearchPage(fixture("rustore_search_empty.html"), 1)

        assertTrue("no matches must not look like a source failure", page.apps.isEmpty())
        assertEquals(0, page.total)
    }

    @Test
    fun searchStillRejectsMarkupThatIsNotASearchScreen() {
        assertThrows(SourceFormatException::class.java) {
            RustoreHtmlParser.parseSearchPage("<html><body><p>gateway timeout</p></body></html>", 1)
        }
    }

    @Test
    fun aCatalogSectionThatRenderedButHoldsNoAppsIsNotAnError() {
        // The category nav proves the catalog page itself rendered.
        val markup = """
            <html><body>
              <h1>Приложения: Пусто</h1>
              <a data-testid="category" href="/catalog/tools">Полезные инструменты</a>
            </body></html>
        """.trimIndent()

        val page = RustoreHtmlParser.parseCatalogPage(markup, "tools", 1)

        assertTrue(page.apps.isEmpty())
        assertFalse(page.hasMore)
    }

    @Test
    fun catalogRejectsMarkupWithoutCards() {
        assertThrows(SourceFormatException::class.java) {
            RustoreHtmlParser.parseCatalogPage("<html><body><h1>Приложения</h1></body></html>", "tools", 1)
        }
    }

    @Test
    fun slugPolicyAcceptsOnlyTheShapeTheSourceUses() {
        listOf("", "tools", "state", "health-and-fitness", "top_apps").forEach {
            assertTrue("$it should be valid", CatalogSlugPolicy.isValid(it))
        }
        listOf("../secrets", "tools/../..", "https://evil.example", "Tools", "a b", "a".repeat(65))
            .forEach { assertFalse("$it must be rejected", CatalogSlugPolicy.isValid(it)) }
        // A rejected slug is now a typed source failure rather than a bare IllegalArgumentException,
        // so the reason can be shown in the user's language instead of a Kotlin message.
        val rejected = assertThrows(SourceFormatException::class.java) {
            CatalogSlugPolicy.requireValid("../etc")
        }
        assertEquals(SourceError.INVALID_SECTION, rejected.error)
        assertEquals("tools", CatalogSlugPolicy.requireValid("/tools/"))
    }
}
