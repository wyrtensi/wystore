package dev.wystore.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogRepositoryTest {

    private class FakeSource : StoreSource {
        var categoryCalls = 0
        var catalogCalls = 0
        var failCategories: Throwable? = null
        var failCatalog: Throwable? = null
        var categoryTitle = "Инструменты"

        override suspend fun search(query: String, page: Int) = SearchPage(emptyList(), page, null)
        override suspend fun details(packageName: String, includeReviews: Boolean) =
            throw UnsupportedOperationException()

        override suspend fun resolveArtifacts(app: StoreApp) = emptyList<DownloadArtifact>()

        override suspend fun categories(): List<StoreCategory> {
            categoryCalls++
            failCategories?.let { throw it }
            return listOf(StoreCategory("tools", categoryTitle))
        }

        override suspend fun catalog(slug: String, page: Int): CatalogPage {
            catalogCalls++
            failCatalog?.let { throw it }
            return CatalogPage(listOf(storeApp("$slug.p$page")), page, lastPage = 3)
        }

        override suspend fun reviews(packageName: String): List<StoreReview> = emptyList()
    }

    /** In-memory stand-in for the Room-backed store. */
    private class FakeCacheStore : CatalogCacheStore {
        var categories: List<StoreCategory> = emptyList()
        val pages = mutableMapOf<String, CatalogPage>()
        var writes = 0

        override suspend fun readCategories() = categories
        override suspend fun writeCategories(categories: List<StoreCategory>) {
            this.categories = categories
            writes++
        }

        override suspend fun readPage(slug: String, page: Int) = pages["$slug#$page"]
        override suspend fun writePage(slug: String, page: CatalogPage) {
            pages["$slug#${page.page}"] = page
            writes++
        }

        override suspend fun clear() {
            categories = emptyList()
            pages.clear()
            remembered.clear()
        }

        val remembered = mutableMapOf<String, String>()

        override suspend fun iconFor(packageName: String): String? = remembered[packageName]
            ?: pages.values.flatMap { it.apps }.firstOrNull { it.packageName == packageName }?.iconUrl

        override suspend fun rememberIcons(icons: Map<String, String>) {
            remembered += icons
        }
    }

    private var clock = 0L
    private fun repository(
        source: StoreSource,
        maxPages: Int = 24,
        cacheStore: CatalogCacheStore = CatalogCacheStore.None
    ) = CatalogRepository(source, cacheStore, ttlMillis = 1000L, maxCachedPages = maxPages, now = { clock })

    @Test
    fun aSecondReadInsideTheTtlDoesNotHitTheSource() = runTest {
        val source = FakeSource()
        val repository = repository(source)

        // categories() merges the curated sections in front of the source's own.
        assertEquals(
            CuratedCategories.ALL.size + 1,
            repository.categories().value.size
        )
        repository.categories()
        repository.catalog("tools", 1)
        repository.catalog("tools", 1)

        assertEquals(1, source.categoryCalls)
        assertEquals(1, source.catalogCalls)
    }

    @Test
    fun anExpiredEntryIsRefetched() = runTest {
        val source = FakeSource()
        val repository = repository(source)
        repository.categories()

        clock += 1001
        source.categoryTitle = "Обновлено"

        // By slug, not by position: a source section now sits next to the curated section that
        // reads it rather than after every one of them.
        assertEquals("Обновлено", repository.categories().value.first { it.slug == "tools" }.title)
        assertEquals(2, source.categoryCalls)
    }

    @Test
    fun aFailedRefreshServesTheCachedValueAndFlagsItStale() = runTest {
        val source = FakeSource()
        val repository = repository(source)
        repository.categories()
        clock += 1001
        source.failCategories = java.io.IOException("no network")

        val result = repository.categories()

        assertTrue("caller must know the value is not fresh", result.stale)
        assertEquals("Инструменты", result.value.first { it.slug == "tools" }.title)
        assertNotNull(result.error)
    }

    @Test
    fun aFailureWithNothingCachedPropagates() = runTest {
        val source = FakeSource().apply { failCatalog = java.io.IOException("no network") }
        val repository = repository(source)

        assertThrows(java.io.IOException::class.java) {
            kotlinx.coroutines.runBlocking { repository.catalog("tools", 1) }
        }
    }

    @Test
    fun theCacheStaysBounded() = runTest {
        val source = FakeSource()
        val repository = repository(source, maxPages = 3)

        repeat(6) { index -> repository.catalog("tools", index + 1) }
        assertEquals(6, source.catalogCalls)

        // The oldest pages were evicted, so re-reading page 1 goes back to the source, while the
        // newest page is still served from cache.
        repository.catalog("tools", 1)
        assertEquals(7, source.catalogCalls)
        repository.catalog("tools", 6)
        assertEquals("newest page must still be cached", 7, source.catalogCalls)
    }

    @Test
    fun forceRefreshBypassesAFreshEntry() = runTest {
        val source = FakeSource()
        val repository = repository(source)
        repository.catalog("tools", 1)

        repository.catalog("tools", 1, forceRefresh = true)

        assertEquals(2, source.catalogCalls)
    }

    @Test
    fun successfulReadsArePersisted() = runTest {
        val store = FakeCacheStore()
        val repository = repository(FakeSource(), cacheStore = store)

        repository.categories()
        repository.catalog("tools", 1)

        assertTrue("the source section is persisted", store.categories.any { it.slug == "tools" })
        assertTrue(
            "curated sections are persisted with the source's",
            store.categories.map { it.slug }.containsAll(CuratedCategories.ALL.map { it.slug })
        )
        assertNotNull(store.pages["tools#1"])
    }

    @Test
    fun aColdProcessFallsBackToDiskWhenTheSourceIsUnreachable() = runTest {
        // Nothing in memory: exactly the state after a restart with no network.
        val store = FakeCacheStore().apply {
            categories = listOf(StoreCategory("tools", "Инструменты"))
            pages["tools#1"] = CatalogPage(listOf(storeApp("cached.app")), 1, lastPage = 4)
        }
        val source = FakeSource().apply {
            failCategories = java.io.IOException("offline")
            failCatalog = java.io.IOException("offline")
        }
        val repository = repository(source, cacheStore = store)

        val categories = repository.categories()
        val catalog = repository.catalog("tools", 1)

        assertTrue(categories.stale)
        assertEquals("Инструменты", categories.value.single().title)
        assertTrue(catalog.stale)
        assertEquals("cached.app", catalog.value.apps.single().packageName)
        assertEquals(4, catalog.value.lastPage)
    }

    @Test
    fun anEmptyDiskCacheStillPropagatesTheFailure() = runTest {
        val source = FakeSource().apply { failCatalog = java.io.IOException("offline") }
        val repository = repository(source, cacheStore = FakeCacheStore())

        assertThrows(java.io.IOException::class.java) {
            kotlinx.coroutines.runBlocking { repository.catalog("tools", 1) }
        }
    }

    @Test
    fun cachedReadsDoNotTouchTheSource() = runTest {
        val store = FakeCacheStore().apply {
            pages["tools#1"] = CatalogPage(listOf(storeApp("cached.app")), 1, lastPage = 2)
        }
        val source = FakeSource()
        val repository = repository(source, cacheStore = store)

        val cached = repository.cachedCatalog("tools", 1)

        assertEquals("cached.app", cached?.apps?.single()?.packageName)
        assertEquals("reading the cache must not fetch", 0, source.catalogCalls)
    }

    @Test
    fun hasMoreReflectsThePagerRatherThanPageFullness() {
        assertTrue(CatalogPage(emptyList(), page = 1, lastPage = 3).hasMore)
        assertFalse(CatalogPage(emptyList(), page = 3, lastPage = 3).hasMore)
        assertFalse("no pager means nothing more is known", CatalogPage(emptyList(), 1, null).hasMore)
    }
}

/**
 * The icon used to be looked up in the cached catalogue *pages*, which are trimmed as browsing
 * moves on. A queue row for an app whose page had since been evicted - and which is not installed,
 * so there is no launcher icon to borrow - was drawn as an empty square next to its name.
 */
class CatalogIconMemoryTest {

    private class IconSource(private val app: StoreApp) : StoreSource {
        override suspend fun search(query: String, page: Int) = SearchPage(emptyList(), page, null)
        override suspend fun details(packageName: String, includeReviews: Boolean) = app
        override suspend fun resolveArtifacts(app: StoreApp) = emptyList<DownloadArtifact>()
        override suspend fun categories() = emptyList<StoreCategory>()
        override suspend fun catalog(slug: String, page: Int) = CatalogPage(listOf(app), page, 1)
        override suspend fun reviews(packageName: String) = emptyList<StoreReview>()
    }

    private class MemoStore : CatalogCacheStore {
        val pages = mutableMapOf<String, CatalogPage>()
        val remembered = mutableMapOf<String, String>()

        override suspend fun readCategories() = emptyList<StoreCategory>()
        override suspend fun writeCategories(categories: List<StoreCategory>) = Unit
        override suspend fun readPage(slug: String, page: Int) = pages["$slug#$page"]
        override suspend fun writePage(slug: String, page: CatalogPage) {
            pages["$slug#${page.page}"] = page
        }

        override suspend fun clear() {
            pages.clear()
            remembered.clear()
        }

        override suspend fun iconFor(packageName: String): String? = remembered[packageName]
            ?: pages.values.flatMap { it.apps }.firstOrNull { it.packageName == packageName }?.iconUrl

        override suspend fun rememberIcons(icons: Map<String, String>) {
            remembered += icons
        }
    }

    @Test
    fun anIconOutlivesThePageItWasSeenOn() = runTest {
        val app = storeApp("ru.yandex.androidkeyboard", iconUrl = "https://icons/keyboard.png")
        val store = MemoStore()
        val repository = CatalogRepository(IconSource(app), store)

        repository.catalog("tools", 1)
        // The page cache is trimmed as browsing moves on; the icon must not go with it.
        store.pages.clear()

        assertEquals("https://icons/keyboard.png", repository.cachedIcon("ru.yandex.androidkeyboard"))
    }

    /** Search and a directly opened app never wrote a page, so they left nothing behind at all. */
    @Test
    fun openingAnAppRemembersItsIcon() = runTest {
        val app = storeApp("ru.yandex.taximeter", iconUrl = "https://icons/pro.png")
        val store = MemoStore()
        val repository = CatalogRepository(IconSource(app), store)

        repository.details("ru.yandex.taximeter")

        assertEquals("https://icons/pro.png", repository.cachedIcon("ru.yandex.taximeter"))
    }

    @Test
    fun anAppWithNoIconAtTheSourceRemembersNothing() = runTest {
        val store = MemoStore()
        val repository = CatalogRepository(IconSource(storeApp("dev.wystore.blank")), store)

        repository.details("dev.wystore.blank")

        assertTrue(store.remembered.isEmpty())
    }
}

private fun storeApp(packageName: String, iconUrl: String? = null) = StoreApp(
    appId = 0,
    packageName = packageName,
    name = packageName,
    publisher = "",
    categories = emptyList(),
    shortDescription = "",
    fullDescription = "",
    iconUrl = iconUrl,
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
