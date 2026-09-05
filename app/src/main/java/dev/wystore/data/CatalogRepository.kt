package dev.wystore.data

import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Cache-first access to the source catalogue.
 *
 * Home and the category browser both re-enter constantly (tab switches, rotation, back navigation),
 * and every miss is a full HTML fetch and parse. Reads are served from memory, then from the
 * on-disk [CatalogCacheStore], and only then from the source; entries are considered fresh for
 * [ttlMillis] and are served stale on failure, so a flaky network degrades to slightly old content
 * instead of an empty screen.
 */
class CatalogRepository(
    private val source: StoreSource,
    private val cacheStore: CatalogCacheStore = CatalogCacheStore.None,
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val maxCachedPages: Int = DEFAULT_MAX_CACHED_PAGES,
    private val maxCachedDetails: Int = DEFAULT_MAX_CACHED_DETAILS,
    private val now: () -> Long = System::currentTimeMillis
) {
    constructor(context: Context, source: StoreSource) :
        this(source, RoomCatalogCacheStore(context))

    private data class Entry<T>(val value: T, val storedAt: Long)

    private val mutex = Mutex()
    private var categories: Entry<List<StoreCategory>>? = null
    private val pages = LinkedHashMap<String, Entry<CatalogPage>>()
    private val detailPages = LinkedHashMap<String, Entry<StoreApp>>()

    /**
     * Result of a catalog read. [stale] says the value came from cache after a failed refresh, so
     * the UI can show it while still telling the user it could not reach the source.
     */
    data class Result<T>(val value: T, val stale: Boolean = false, val error: Throwable? = null)

    /**
     * A curated category, assembled by reading its source sections and applying its allowlist.
     * Falls back to the plain source section when the slug is not a curated one.
     */
    suspend fun curatedCatalog(
        category: CuratedCategory,
        forceRefresh: Boolean = false
    ): Result<CatalogPage> = coroutineScope {
        // A curated section reads several source sections, each several pages deep. Fetching them
        // one after another made opening "Банки" take as long as every page added together; they
        // are independent requests to one host, so they go out together and the shared connection
        // pool decides how many are actually in flight.
        val requests = category.sourceSlugs.flatMap { sourceSlug ->
            (1..category.pagesPerSource).map { page -> sourceSlug to page }
        }
        val results = requests.map { (sourceSlug, page) ->
            async { runCatching { catalog(sourceSlug, page, forceRefresh) } }
        }.awaitAll()

        val collected = mutableListOf<StoreApp>()
        var stale = false
        var failure: Throwable? = null
        for (result in results) {
            result.getOrNull()?.let {
                collected += it.value.apps
                stale = stale || it.stale
            } ?: run { failure = result.exceptionOrNull() }
        }
        if (collected.isEmpty()) throw failure ?: IllegalStateException("Раздел каталога пуст")
        // One page: the allowlist is finite, so there is nothing further to page through.
        Result(
            value = CatalogPage(apps = category.select(collected), page = 1, lastPage = 1),
            stale = stale,
            error = failure
        )
    }

    /**
     * A single app page, cached in memory for the session.
     *
     * The page is two requests: the JSON overview and the HTML page that carries reviews and the
     * changelog. Tapping into an app, going back and tapping in again used to repeat both, which
     * is the most common navigation in the whole app.
     */
    suspend fun details(packageName: String, forceRefresh: Boolean = false): StoreApp {
        val cached = mutex.withLock { detailPages[packageName] }
        if (!forceRefresh && cached != null && !isExpired(cached.storedAt)) {
            return cached.value
        }
        return runCatching { source.details(packageName, includeReviews = true) }
            .fold(
                onSuccess = { fresh ->
                    mutex.withLock {
                        detailPages[packageName] = Entry(fresh, now())
                        while (detailPages.size > maxCachedDetails) {
                            val oldest = detailPages.keys.firstOrNull() ?: break
                            detailPages.remove(oldest)
                        }
                    }
                    fresh
                },
                // A failed refresh falls back to whatever was already shown rather than emptying
                // a page the user is looking at.
                onFailure = { error -> cached?.value ?: throw error }
            )
    }

    suspend fun categories(forceRefresh: Boolean = false): Result<List<StoreCategory>> {
        val cached = mutex.withLock { categories }
        if (!forceRefresh && cached != null && !isExpired(cached.storedAt)) {
            return Result(cached.value)
        }
        return runCatching { source.categories() }
            .fold(
                onSuccess = { fresh ->
                    // Wy Store's own sections lead; the source's remain available underneath.
                    val merged = CuratedCategories.merge(fresh)
                    mutex.withLock { categories = Entry(merged, now()) }
                    runCatching { cacheStore.writeCategories(merged) }
                    Result(merged)
                },
                onFailure = { error ->
                    val fallback = cached?.value
                        ?: runCatching { cacheStore.readCategories() }.getOrNull()?.takeIf { it.isNotEmpty() }
                        ?: throw error
                    Result(fallback, stale = true, error = error)
                }
            )
    }

    suspend fun catalog(slug: String, page: Int = 1, forceRefresh: Boolean = false): Result<CatalogPage> {
        val key = cacheKey(slug, page)
        val cached = mutex.withLock { pages[key] }
        if (!forceRefresh && cached != null && !isExpired(cached.storedAt)) {
            return Result(cached.value)
        }
        return runCatching { source.catalog(slug, page) }
            .fold(
                onSuccess = { fresh ->
                    remember(key, fresh)
                    runCatching { cacheStore.writePage(slug, fresh) }
                    Result(fresh)
                },
                onFailure = { error ->
                    val fallback = cached?.value
                        ?: runCatching { cacheStore.readPage(slug, page) }.getOrNull()
                        ?: throw error
                    remember(key, fallback)
                    Result(fallback, stale = true, error = error)
                }
            )
    }

    /**
     * Content the app can show before the network answers. Returns null when nothing was ever
     * cached, so the caller can tell "no data yet" from "here is what we had".
     */
    suspend fun cachedCatalog(slug: String, page: Int = 1): CatalogPage? {
        mutex.withLock { pages[cacheKey(slug, page)] }?.let { return it.value }
        return runCatching { cacheStore.readPage(slug, page) }.getOrNull()
    }

    suspend fun cachedCategories(): List<StoreCategory> {
        mutex.withLock { categories }?.let { return it.value }
        return runCatching { cacheStore.readCategories() }.getOrDefault(emptyList())
    }

    suspend fun clear() {
        mutex.withLock {
            categories = null
            pages.clear()
            detailPages.clear()
        }
        runCatching { cacheStore.clear() }
    }

    private suspend fun remember(key: String, page: CatalogPage) = mutex.withLock {
        pages[key] = Entry(page, now())
        // Bounded so browsing deep into several sections cannot grow without limit.
        while (pages.size > maxCachedPages) {
            val oldest = pages.keys.firstOrNull() ?: break
            pages.remove(oldest)
        }
    }

    private fun isExpired(storedAt: Long): Boolean = now() - storedAt >= ttlMillis

    private fun cacheKey(slug: String, page: Int) = "$slug#$page"

    companion object {
        const val DEFAULT_TTL_MILLIS = 15 * 60 * 1000L
        const val DEFAULT_MAX_CACHED_PAGES = 24
        const val DEFAULT_MAX_CACHED_DETAILS = 16

        @Volatile
        private var instance: CatalogRepository? = null

        /**
         * The shared cache. Home and the category browser each used to build their own, so
         * everything Home had just fetched was fetched again the moment a category was opened.
         */
        fun getInstance(context: Context): CatalogRepository = instance ?: synchronized(this) {
            instance ?: CatalogRepository(
                context.applicationContext,
                RuStoreSource.getInstance(context)
            ).also { instance = it }
        }
    }
}
