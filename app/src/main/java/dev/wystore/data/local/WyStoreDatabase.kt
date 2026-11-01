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
        CatalogAppEntity::class,
        PackageIconEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class WyStoreDatabase : RoomDatabase() {

    abstract val updateQueueDao: UpdateQueueDao

    abstract val catalogCacheDao: CatalogCacheDao

    companion object {
        private const val DATABASE_NAME = "wystore.db"

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE update_queue ADD COLUMN requiresUpdateOwnership INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE update_queue ADD COLUMN requiresUserConfirmation INTEGER NOT NULL DEFAULT 0")
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
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { instance = it }
            }
    }
}
