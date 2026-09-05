package dev.wystore.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The catalog cache added in v2 shares a database with the update queue and with artifacts already
 * downloaded to private storage, so the upgrade must preserve them. A destructive fallback would
 * drop pending installs and leak their APK files.
 */
@RunWith(AndroidJUnit4::class)
class WyStoreDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WyStoreDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migratingToVersion2KeepsTheQueueAndAddsTheCatalogCache() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO update_queue
                    (id, packageName, label, versionName, versionCode, source, state, priority,
                     position, downloadedBytes, totalBytes, errorCode, errorDetail, signingDigests,
                     githubRepositoryOwner, githubRepositoryName, githubReleaseId, createdAt, updatedAt)
                VALUES
                    ('row-1', 'dev.example.app', 'Example', '1.2.3', 123, 'RUSTORE', 'READY_TO_INSTALL',
                     0, 0, 0, 0, NULL, NULL, 'abc123', NULL, NULL, NULL, 100, 200)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO update_artifacts (queueId, path, size, sourceHash, createdAt, lastAccessedAt)
                VALUES ('row-1', '/data/example/artifact_0.apk', 4096, NULL, 100, 200)
                """.trimIndent()
            )
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 2, true, WyStoreDatabase.MIGRATION_1_2)

        migrated.query("SELECT packageName, versionCode, signingDigests, state FROM update_queue").use { cursor ->
            assertTrue("the pending install must survive the upgrade", cursor.moveToFirst())
            assertEquals("dev.example.app", cursor.getString(0))
            assertEquals(123L, cursor.getLong(1))
            assertEquals("abc123", cursor.getString(2))
            assertEquals("READY_TO_INSTALL", cursor.getString(3))
        }
        migrated.query("SELECT path FROM update_artifacts").use { cursor ->
            assertTrue("the downloaded artifact row must survive", cursor.moveToFirst())
            assertEquals("/data/example/artifact_0.apk", cursor.getString(0))
        }
        // The new tables exist and are queryable; runMigrationsAndValidate already checked the shape.
        migrated.query("SELECT COUNT(*) FROM catalog_categories").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        migrated.query("SELECT COUNT(*) FROM catalog_apps").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        migrated.close()
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
