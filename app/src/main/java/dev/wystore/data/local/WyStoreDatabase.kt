package dev.wystore.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        UpdateQueueEntity::class,
        UpdateArtifactEntity::class,
        InstallSessionEntity::class,
        CatalogCategoryEntity::class,
        CatalogAppEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class WyStoreDatabase : RoomDatabase() {

    abstract val updateQueueDao: UpdateQueueDao

    abstract val catalogCacheDao: CatalogCacheDao

    companion object {
        private const val DATABASE_NAME = "wystore.db"

        /**
         * Adds the catalog cache. It is a pure cache, but the same database holds the update queue
         * and the artifacts already downloaded to disk, so this is a real migration rather than a
         * destructive one: falling back would drop pending installs and leak their APK files.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `catalog_categories` (
                        `slug` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `iconUrl` TEXT,
                        `position` INTEGER NOT NULL,
                        `cachedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`slug`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `catalog_apps` (
                        `slug` TEXT NOT NULL,
                        `page` INTEGER NOT NULL,
                        `position` INTEGER NOT NULL,
                        `packageName` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `categoryLabel` TEXT,
                        `iconUrl` TEXT,
                        `rating` REAL,
                        `lastPage` INTEGER,
                        `cachedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`slug`, `page`, `position`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_catalog_apps_slug_page` ON `catalog_apps` (`slug`, `page`)")
            }
        }

        @Volatile
        private var instance: WyStoreDatabase? = null

        fun getInstance(context: Context): WyStoreDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WyStoreDatabase::class.java,
                    DATABASE_NAME
                ).addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
