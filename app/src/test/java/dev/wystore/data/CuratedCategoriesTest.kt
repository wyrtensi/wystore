package dev.wystore.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CuratedCategoriesTest {

    private fun app(packageName: String) = StoreApp(
        appId = 0,
        packageName = packageName,
        name = packageName,
        publisher = "",
        categories = emptyList(),
        shortDescription = "",
        fullDescription = "",
        iconUrl = null,
        screenshots = emptyList(),
        rating = null,
        ratingCount = null,
        downloadsText = null,
        versionName = "",
        versionCode = 0,
        updatedAt = null,
        sizeBytes = 0,
        minAndroidVersion = null,
        signatureHint = null,
        sourceVersionId = null
    )

    @Test
    fun theCuratedSectionsAreTheOnesTheStoreOffers() {
        assertEquals(
            listOf(
                "Банки и платежи",
                "Маркетплейсы",
                "Продукты и еда",
                "Госуслуги",
                "Общение",
                "Кино и музыка",
                "Транспорт",
                "Путешествия",
                "Здоровье и аптеки",
                "Связь"
            ),
            CuratedCategories.ALL.map { it.title }
        )
    }

    /**
     * The titles above are the Russian originals; what a screen shows comes from `titleRes`, so a
     * section added without one would silently stay Russian in an English interface.
     */
    @Test
    fun everyCuratedSectionCarriesATranslatableName() {
        val untranslated = CuratedCategories.ALL.filter { it.titleRes == 0 }.map { it.slug }
        assertEquals(emptyList<String>(), untranslated)
        assertEquals(
            CuratedCategories.ALL.map { it.titleRes }.distinct().size,
            CuratedCategories.ALL.size
        )
    }

    @Test
    fun aStoreCategoryKeepsTheResourceForItsName() {
        val banks = CuratedCategories.find("wy-banks")!!
        assertEquals(banks.titleRes, banks.toStoreCategory().titleRes)
    }

    @Test
    fun anAllowlistDecidesBothMembershipAndOrder() {
        val banks = CuratedCategories.find("wy-banks")!!
        // Deliberately shuffled, and carrying a loan app the source lists under Финансы.
        val fromSource = listOf(
            app("com.andrey.all_zaims"),
            app("ru.alfabank.mobile.android"),
            app("ru.sberbankmobile"),
            app("fin.button.app")
        )

        val selected = banks.select(fromSource).map { it.packageName }

        assertEquals(listOf("ru.sberbankmobile", "ru.alfabank.mobile.android"), selected)
    }

    @Test
    fun duplicatesAcrossSourceSectionsAreCollapsed() {
        val state = CuratedCategories.find("wy-state")!!
        val fromSource = listOf(app("ru.rostel"), app("ru.rostel"), app("ru.fns.lkfl"))

        assertEquals(listOf("ru.rostel", "ru.fns.lkfl"), state.select(fromSource).map { it.packageName })
    }

    @Test
    fun theStateSectionPullsFromMoreThanOneSourceSection() {
        val state = CuratedCategories.find("wy-state")!!

        // "Налоги ФЛ" and "Мой налог" are filed under Финансы by the source but belong here.
        assertEquals(listOf("state", "finance"), state.sourceSlugs)
        assertTrue(state.packages.contains("ru.fns.lkfl"))
        assertTrue(state.packages.contains("com.gnivts.selfemployed"))
        assertTrue("Госуслуги Дом must be included", state.packages.contains("ru.sigma.gisgkh"))
    }

    @Test
    fun aCuratedSectionIsFollowedByTheSourceSectionItIsBuiltFrom() {
        val merged = CuratedCategories.merge(
            listOf(StoreCategory("finance", "Финансы"), StoreCategory("all", "Все приложения"))
        )

        assertEquals(CuratedCategories.ALL.size + 2, merged.size)
        // The two nearest neighbours in meaning used to be the furthest apart on screen: every
        // curated section first, then everything the source publishes.
        val slugs = merged.map { it.slug }
        assertEquals("wy-banks", slugs.first())
        assertEquals("finance", slugs[1])
        // Nothing claimed by a curated section is left over for the tail.
        assertEquals("all", slugs.last())
    }

    /** A source section named by two curated ones belongs to the first: a rail has no duplicates. */
    @Test
    fun aSourceSectionIsPlacedOnceEvenWhenTwoSectionsReadIt() {
        val merged = CuratedCategories.merge(listOf(StoreCategory("finance", "Финансы")))

        assertEquals(1, merged.count { it.slug == "finance" })
        assertEquals("wy-banks", merged[merged.indexOfFirst { it.slug == "finance" } - 1].slug)
    }

    @Test
    fun mergingDoesNotDuplicateASlugTheSourceAlsoUses() {
        val merged = CuratedCategories.merge(listOf(StoreCategory("wy-banks", "Something else")))

        assertEquals(CuratedCategories.ALL.size, merged.size)
        assertEquals("Банки и платежи", merged.first { it.slug == "wy-banks" }.title)
    }

    @Test
    fun everyCuratedSlugIsARoutableIdentifierAndResolvable() {
        CuratedCategories.ALL.forEach { category ->
            assertTrue(category.slug, CatalogSlugPolicy.isValid(category.slug))
            assertNotNull(CuratedCategories.find(category.slug))
            assertTrue("${category.slug} needs a source", category.sourceSlugs.isNotEmpty())
            assertTrue("${category.slug} needs an icon", !category.iconUrl.isNullOrBlank())
        }
        assertNull(CuratedCategories.find("not-a-section"))
    }

    @Test
    fun everyCuratedSlugIsDistinctFromTheSourceNamespace() {
        // The prefix keeps a curated slug from ever being sent to the source as a section path.
        assertTrue(CuratedCategories.ALL.all { it.slug.startsWith("wy-") })
        assertEquals(
            CuratedCategories.ALL.size,
            CuratedCategories.ALL.map { it.slug }.distinct().size
        )
    }

    @Test
    fun everyCuratedPackageIsNamedOnlyOnceInItsCategory() {
        // A package listed twice would show the same card twice in the section.
        CuratedCategories.ALL.forEach { category ->
            assertEquals(
                category.slug,
                category.packages.size,
                category.packages.distinct().size
            )
        }
    }

    @Test
    fun anAllowlistedSectionReadsDeepEnoughToFindItsApps() {
        // A curated app can sit past the first page of its source section.
        assertTrue(CuratedCategories.ALL.filter { it.packages.isNotEmpty() }.all { it.pagesPerSource >= 2 })
    }
}
