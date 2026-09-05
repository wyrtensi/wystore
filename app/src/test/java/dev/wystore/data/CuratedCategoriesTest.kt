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
                "Банки",
                "Маркетплейсы",
                "Супермаркеты и рестораны",
                "Государственное",
                "Мессенджеры и соцсети",
                "Для жизни"
            ),
            CuratedCategories.ALL.map { it.title }
        )
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
    fun curatedSectionsLeadAndSourceSectionsFollow() {
        val merged = CuratedCategories.merge(
            listOf(StoreCategory("finance", "Финансы"), StoreCategory("all", "Все приложения"))
        )

        assertEquals(CuratedCategories.ALL.size + 2, merged.size)
        assertEquals("wy-banks", merged.first().slug)
        assertEquals(listOf("finance", "all"), merged.takeLast(2).map { it.slug })
    }

    @Test
    fun mergingDoesNotDuplicateASlugTheSourceAlsoUses() {
        val merged = CuratedCategories.merge(listOf(StoreCategory("wy-banks", "Something else")))

        assertEquals(CuratedCategories.ALL.size, merged.size)
        assertEquals("Банки", merged.first { it.slug == "wy-banks" }.title)
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
    fun anAllowlistedSectionReadsDeepEnoughToFindItsApps() {
        // A curated app can sit past the first page of its source section.
        assertTrue(CuratedCategories.ALL.filter { it.packages.isNotEmpty() }.all { it.pagesPerSource >= 2 })
    }
}
