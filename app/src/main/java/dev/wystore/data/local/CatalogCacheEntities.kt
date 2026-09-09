package dev.wystore.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * One catalog section as the source listed it. Cached so Home has something to show on a cold start
 * and on a network that is not there yet.
 */
@Entity(tableName = "catalog_categories")
data class CatalogCategoryEntity(
    @androidx.room.PrimaryKey
    val slug: String,
    val title: String,
    val iconUrl: String?,
    val position: Int,
    val cachedAt: Long
)

/**
 * One card from a catalog page. [position] preserves the source's ordering, which is the ranking the
 * catalogue is actually meaningful in.
 */
@Entity(
    tableName = "catalog_apps",
    primaryKeys = ["slug", "page", "position"],
    indices = [Index(value = ["slug", "page"])]
)
data class CatalogAppEntity(
    val slug: String,
    val page: Int,
    val position: Int,
    val packageName: String,
    val name: String,
    val categoryLabel: String?,
    val iconUrl: String?,
    val rating: Double?,
    val lastPage: Int?,
    val cachedAt: Long
)

/**
 * The icon last seen for a package, kept apart from the pages it was seen on.
 *
 * Icons used to be read out of [CatalogAppEntity], which is a cache of catalogue *pages*: it holds
 * only the sections browsed recently, is trimmed to a fixed number of them, and is never written by
 * search or by an app opened directly. A queue row for an app that is not installed yet therefore
 * had nothing to draw - the download was under way and the card next to it was an empty square.
 * This remembers the package instead of the page, so once the app has seen an icon it keeps it.
 */
@Entity(tableName = "package_icons")
data class PackageIconEntity(
    @androidx.room.PrimaryKey
    val packageName: String,
    val iconUrl: String,
    val cachedAt: Long
)

@Dao
abstract class CatalogCacheDao {

    @Query("SELECT * FROM catalog_categories ORDER BY position ASC")
    abstract suspend fun categories(): List<CatalogCategoryEntity>

    @Query("SELECT * FROM catalog_apps WHERE slug = :slug AND page = :page ORDER BY position ASC")
    abstract suspend fun page(slug: String, page: Int): List<CatalogAppEntity>

    /**
     * The icon the catalogue last showed for a package, whichever section it came from.
     *
     * Queue rows carry no icon of their own, and an app that is not installed yet has none on the
     * device either, so cards for it were drawn blank.
     */
    @Query("SELECT iconUrl FROM catalog_apps WHERE packageName = :packageName AND iconUrl IS NOT NULL LIMIT 1")
    abstract suspend fun pageIconFor(packageName: String): String?

    @Query("SELECT iconUrl FROM package_icons WHERE packageName = :packageName")
    abstract suspend fun rememberedIconFor(packageName: String): String?

    /** The remembered icon first: it outlives the page the icon was first seen on. */
    @Transaction
    open suspend fun iconFor(packageName: String): String? =
        rememberedIconFor(packageName) ?: pageIconFor(packageName)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertIcons(entities: List<PackageIconEntity>)

    @Query(
        "DELETE FROM package_icons WHERE packageName NOT IN " +
            "(SELECT packageName FROM package_icons ORDER BY cachedAt DESC LIMIT :keep)"
    )
    abstract suspend fun trimIcons(keep: Int)

    /** Bounded: a few hundred short strings, so browsing for a year cannot grow it without limit. */
    @Transaction
    open suspend fun rememberIcons(entities: List<PackageIconEntity>, keep: Int = MAX_REMEMBERED_ICONS) {
        if (entities.isEmpty()) return
        insertIcons(entities)
        trimIcons(keep)
    }

    @Query("DELETE FROM package_icons")
    abstract suspend fun clearIcons()

    @Query("DELETE FROM catalog_categories")
    abstract suspend fun clearCategories()

    @Query("DELETE FROM catalog_apps WHERE slug = :slug AND page = :page")
    abstract suspend fun clearPage(slug: String, page: Int)

    @Query("DELETE FROM catalog_apps")
    abstract suspend fun clearApps()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertCategories(entities: List<CatalogCategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertApps(entities: List<CatalogAppEntity>)

    @Query("SELECT DISTINCT slug || '#' || page FROM catalog_apps ORDER BY cachedAt DESC LIMIT -1 OFFSET :keep")
    abstract suspend fun stalePageKeys(keep: Int): List<String>

    @Transaction
    open suspend fun replaceCategories(entities: List<CatalogCategoryEntity>) {
        clearCategories()
        insertCategories(entities)
    }

    /**
     * Replaces one page and drops the least recently cached pages beyond [maxCachedPages], so
     * browsing deep into several sections cannot grow the database without bound.
     */
    @Transaction
    open suspend fun replacePage(
        slug: String,
        page: Int,
        entities: List<CatalogAppEntity>,
        maxCachedPages: Int
    ) {
        clearPage(slug, page)
        insertApps(entities)
        for (key in stalePageKeys(maxCachedPages)) {
            val separator = key.lastIndexOf('#')
            if (separator <= 0) continue
            val staleSlug = key.substring(0, separator)
            val stalePage = key.substring(separator + 1).toIntOrNull() ?: continue
            clearPage(staleSlug, stalePage)
        }
    }

    companion object {
        const val MAX_REMEMBERED_ICONS = 400
    }
}
