package dev.wystore.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        UpdateQueueEntity::class,
        UpdateArtifactEntity::class,
        InstallSessionEntity::class,
        CatalogCategoryEntity::class,
        CatalogAppEntity::class,
        PackageIconEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class WyStoreDatabase : RoomDatabase() {

    abstract val updateQueueDao: UpdateQueueDao

    abstract val catalogCacheDao: CatalogCacheDao

    companion object {
        private const val DATABASE_NAME = "wystore.db"

        @Volatile
        private var instance: WyStoreDatabase? = null

        fun getInstance(context: Context): WyStoreDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WyStoreDatabase::class.java,
                    DATABASE_NAME
                )
                    .build()
                    .also { instance = it }
            }
    }
}
