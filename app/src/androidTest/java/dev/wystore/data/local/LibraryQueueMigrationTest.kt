package dev.wystore.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LibraryQueueMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), WyStoreDatabase::class.java)

    @Test fun artifactProgressMigrationPreservesAnExistingQueue() {
        val name = "library-artifact-progress-migration"
        helper.createDatabase(name, 6).apply {
            execSQL("INSERT INTO update_queue (id, packageName, label, versionName, versionCode, source, state, " +
                "priority, position, downloadedBytes, totalBytes, signingDigests, createdAt, updatedAt) " +
                "VALUES ('existing', 'org.example.app', 'Example', '1', 1, 'WY_STORE', 'DOWNLOADING', " +
                "10, 0, 50, 100, 'abc', 1, 2)")
            close()
        }
        helper.runMigrationsAndValidate(name, 7, true, WyStoreDatabase.MIGRATION_6_7).apply {
            query("SELECT state, downloadedBytes, totalBytes, artifactIndex, artifactCount FROM update_queue").use { row ->
                assertTrue(row.moveToFirst())
                assertEquals("DOWNLOADING", row.getString(0))
                assertEquals(50L, row.getLong(1)); assertEquals(100L, row.getLong(2))
                assertEquals(1, row.getInt(3)); assertEquals(1, row.getInt(4))
            }
            close()
        }
    }

    @Test fun ownershipAndConfirmationSurviveLibraryMigration() {
        val name = "library-queue-migration"
        helper.createDatabase(name, 5).apply {
            execSQL("INSERT INTO update_queue (id, packageName, label, versionName, versionCode, source, state, " +
                "priority, position, downloadedBytes, totalBytes, signingDigests, createdAt, updatedAt, " +
                "requiresUpdateOwnership, requiresUserConfirmation) VALUES ('existing', 'org.example.app', 'Example', " +
                "'1', 1, 'GITHUB', 'READY_TO_INSTALL', 10, 0, 100, 100, 'abc', 1, 2, 1, 1)")
            close()
        }
        helper.runMigrationsAndValidate(name, 6, true, WyStoreDatabase.MIGRATION_5_6).apply {
            query("SELECT source, state, requiresUpdateOwnership, requiresUserConfirmation, sourceRevision, libraryRequestJson " +
                "FROM update_queue WHERE id = 'existing'").use { row ->
                assertTrue(row.moveToFirst())
                assertEquals("GITHUB", row.getString(0)); assertEquals("READY_TO_INSTALL", row.getString(1))
                assertEquals(1, row.getInt(2)); assertEquals(1, row.getInt(3))
                assertEquals(0L, row.getLong(4)); assertTrue(row.isNull(5))
            }
            close()
        }
    }
}
