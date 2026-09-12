package dev.wystore.ui.components

import dev.wystore.R
import dev.wystore.data.CatalogSlugPolicy
import dev.wystore.data.StoreCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What a section of the catalogue is called on screen.
 *
 * The source publishes its section names in Russian, so the rail of tiles stayed Russian above an
 * otherwise translated screen. The same slugs were already named in [CategoryLabels] for the tags
 * under a card, so the rail reads them now too - which makes the table's coverage worth pinning: a
 * slug that quietly stops matching sends the tile back to Russian without anything failing.
 */
class CategoryTitleTest {

    /** The sections the catalogue served when this was written, by their slugs in its own URLs. */
    private val published = listOf(
        "finance", "state", "tools", "transport", "purchases", "social", "entertainment",
        "adsandservices", "business", "health", "travelling", "education", "books", "lifestyle",
        "sport", "news", "parenting", "pets", "gambling", "foodanddrink"
    )

    private fun section(slug: String, published: String) = StoreCategory(slug = slug, title = published)

    @Test
    fun `every section the catalogue publishes has a name in the interface language`() {
        published.forEach { slug ->
            assertNotNull(
                "$slug would keep the name the catalogue published",
                CategoryLabels.titleRes(section(slug, "Раздел"))
            )
        }
    }

    @Test
    fun `every slug is one the catalogue would accept`() {
        // A typo here is invisible otherwise: the tile simply keeps the Russian name forever.
        published.forEach { assertEquals(it, CatalogSlugPolicy.requireValid(it)) }
    }

    @Test
    fun `a section we have no name for keeps the one the catalogue published`() {
        assertNull(CategoryLabels.titleRes(section("a-section-invented-tomorrow", "Новый раздел")))
    }

    @Test
    fun `a section Wy Store assembled itself keeps its own name`() {
        val curated = StoreCategory(
            slug = "wy-banks",
            title = "Банки и платежи",
            titleRes = R.string.wy_category_banks
        )

        // Not the source's "finance", which its sourceSlugs read from: the curated section is
        // narrower than the catalogue's and says so.
        assertEquals(R.string.wy_category_banks, CategoryLabels.titleRes(curated))
    }
}
