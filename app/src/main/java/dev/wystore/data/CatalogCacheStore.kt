package dev.wystore.data

import android.content.Context
import dev.wystore.data.local.CatalogAppEntity
import dev.wystore.data.local.CatalogCacheDao
import dev.wystore.data.local.CatalogCategoryEntity
import dev.wystore.data.local.WyStoreDatabase

/**
 * Persistence for [CatalogRepository].
 *
 * The in-memory cache only survives while the process does, so a cold start always showed empty
 * placeholders until the network answered. This keeps the last catalogue on disk so Home can render
 * immediately and still has something to show with no connection.
 */
interface CatalogCacheStore {
    suspend fun readCategories(): List<StoreCategory>
    suspend fun writeCategories(categories: List<StoreCategory>)
    suspend fun readPage(slug: String, page: Int): CatalogPage?
    suspend fun writePage(slug: String, page: CatalogPage)
    suspend fun clear()

    /** The icon the catalogue last showed for a package, for rows that carry none of their own. */
    suspend fun iconFor(packageName: String): String?

    /** Does nothing; used where persistence is not wanted, such as in tests. */
    object None : CatalogCacheStore {
        override suspend fun readCategories(): List<StoreCategory> = emptyList()
        override suspend fun writeCategories(categories: List<StoreCategory>) = Unit
        override suspend fun readPage(slug: String, page: Int): CatalogPage? = null
        override suspend fun writePage(slug: String, page: CatalogPage) = Unit
        override suspend fun clear() = Unit
        override suspend fun iconFor(packageName: String): String? = null
    }
}

class RoomCatalogCacheStore(
    private val dao: CatalogCacheDao,
    private val maxCachedPages: Int = DEFAULT_MAX_CACHED_PAGES,
    private val now: () -> Long = System::currentTimeMillis
) : CatalogCacheStore {

    constructor(context: Context) : this(WyStoreDatabase.getInstance(context).catalogCacheDao)

    override suspend fun readCategories(): List<StoreCategory> =
        dao.categories().map { StoreCategory(slug = it.slug, title = it.title, iconUrl = it.iconUrl) }

    override suspend fun writeCategories(categories: List<StoreCategory>) {
        val stamp = now()
        dao.replaceCategories(
            categories.mapIndexed { index, category ->
                CatalogCategoryEntity(
                    slug = category.slug,
                    title = category.title,
                    iconUrl = category.iconUrl,
                    position = index,
                    cachedAt = stamp
                )
            }
        )
    }

    override suspend fun readPage(slug: String, page: Int): CatalogPage? {
        val rows = dao.page(slug, page)
        if (rows.isEmpty()) return null
        return CatalogPage(
            apps = rows.map { it.toStoreApp() },
            page = page,
            lastPage = rows.first().lastPage
        )
    }

    override suspend fun writePage(slug: String, page: CatalogPage) {
        val stamp = now()
        dao.replacePage(
            slug = slug,
            page = page.page,
            entities = page.apps.mapIndexed { index, app ->
                CatalogAppEntity(
                    slug = slug,
                    page = page.page,
                    position = index,
                    packageName = app.packageName,
                    name = app.name,
                    categoryLabel = app.categories.firstOrNull(),
                    iconUrl = app.iconUrl,
                    rating = app.rating,
                    lastPage = page.lastPage,
                    cachedAt = stamp
                )
            },
            maxCachedPages = maxCachedPages
        )
    }

    override suspend fun clear() {
        dao.clearCategories()
        dao.clearApps()
    }

    override suspend fun iconFor(packageName: String): String? = dao.iconFor(packageName)

    companion object {
        const val DEFAULT_MAX_CACHED_PAGES = 24
    }
}

/**
 * Cards carry only what a listing shows. Version, size and description come from the details
 * endpoint, so the placeholders here mirror what the parser produces for a live card.
 */
private fun CatalogAppEntity.toStoreApp(): StoreApp = StoreApp(
    appId = 0,
    packageName = packageName,
    name = name,
    publisher = "",
    categories = listOfNotNull(categoryLabel),
    shortDescription = "",
    fullDescription = "",
    iconUrl = iconUrl,
    screenshots = emptyList(),
    rating = rating,
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
