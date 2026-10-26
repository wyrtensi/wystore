package dev.wystore.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdateOwnershipMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), WyStoreDatabase::class.java)

    @Test
    fun existingQueueRowsSurviveWithoutBecomingOwnershipTransfers() {
        helper.createDatabase("update-ownership-migration-test", 3).apply {
            execSQL(
                "INSERT INTO update_queue (id, packageName, label, versionName, versionCode, source, state, " +
                    "priority, position, downloadedBytes, totalBytes, signingDigests, createdAt, updatedAt) " +
                    "VALUES ('existing', 'example.app', 'Example', '1', 1, 'RUSTORE', 'READY_TO_INSTALL', " +
                    "10, 0, 100, 100, 'abc', 1, 2)"
            )
            close()
        }

        helper.runMigrationsAndValidate("update-ownership-migration-test", 4, true, WyStoreDatabase.MIGRATION_3_4).apply {
            query("SELECT label, state, downloadedBytes, requiresUpdateOwnership FROM update_queue WHERE id = 'existing'").use {
                check(it.moveToFirst())
                assertEquals("Example", it.getString(0))
                assertEquals("READY_TO_INSTALL", it.getString(1))
                assertEquals(100L, it.getLong(2))
                assertEquals(0, it.getInt(3))
            }
            close()
        }
    }
}
