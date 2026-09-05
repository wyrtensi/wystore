package dev.wystore.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.wystore.data.ManagedSource
import dev.wystore.updates.model.QueueState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class UpdateQueueDaoTest {

    private lateinit var context: Context
    private lateinit var db: WyStoreDatabase
    private lateinit var dao: UpdateQueueDao

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, WyStoreDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.updateQueueDao
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun atomicUpsertAndUniquePackageVersionSource() {
        runBlocking {
            val entity1 = UpdateQueueEntity(
                id = "id-1",
                packageName = "dev.example.app",
                label = "Example App",
                versionName = "1.0.0",
                versionCode = 100L,
                source = ManagedSource.RUSTORE.name,
                state = QueueState.AVAILABLE.name,
                priority = 0,
                position = 0
            )
            dao.upsertAtomic(entity1)
            assertEquals(1, dao.getAll().size)

            // Upsert with updated label but same package, versionCode, source replaces/updates cleanly
            val entity1Updated = entity1.copy(
                id = "id-1",
                label = "Example App Updated",
                priority = 5
            )
            dao.upsertAtomic(entity1Updated)
            val loaded = dao.getById("id-1")
            assertNotNull(loaded)
            assertEquals("Example App Updated", loaded?.label)
            assertEquals(5, loaded?.priority)
        }
    }

    @Test
    fun stableOrderingAndNextEligible() {
        runBlocking {
            val firstItem = UpdateQueueEntity(
                id = "item-1",
                packageName = "z.package",
                label = "Z App",
                versionName = "1.0",
                versionCode = 1L,
                source = ManagedSource.RUSTORE.name,
                state = QueueState.AVAILABLE.name,
                priority = 10,
                position = 0
            )
            val secondItem = UpdateQueueEntity(
                id = "item-2",
                packageName = "second.package",
                label = "Second App",
                versionName = "1.0",
                versionCode = 1L,
                source = ManagedSource.RUSTORE.name,
                state = QueueState.AVAILABLE.name,
                priority = 100, // Higher priority than firstItem
                position = 0
            )
            val nonEligibleItem = UpdateQueueEntity(
                id = "item-3",
                packageName = "failed.package",
                label = "Failed App",
                versionName = "1.0",
                versionCode = 1L,
                source = ManagedSource.RUSTORE.name,
                state = QueueState.FAILED.name,
                priority = 200,
                position = 0
            )

            dao.upsertAtomic(firstItem)
            dao.upsertAtomic(secondItem)
            dao.upsertAtomic(nonEligibleItem)

            val next = dao.nextEligible()
            assertNotNull(next)
            assertEquals("second.package", next?.packageName)
        }
    }

    @Test
    fun singleActiveItemEnforcement() {
        runBlocking {
            val item1 = UpdateQueueEntity(
                id = "download-1",
                packageName = "app.first",
                label = "App 1",
                versionName = "1.0",
                versionCode = 1L,
                source = ManagedSource.RUSTORE.name,
                state = QueueState.AVAILABLE.name,
                priority = 0,
                position = 0
            )
            val item2 = UpdateQueueEntity(
                id = "download-2",
                packageName = "app.second",
                label = "App 2",
                versionName = "1.0",
                versionCode = 1L,
                source = ManagedSource.RUSTORE.name,
                state = QueueState.AVAILABLE.name,
                priority = 0,
                position = 1
            )

            dao.upsertAtomic(item1)
            dao.upsertAtomic(item2)

            // Transition first item to DOWNLOADING
            dao.transitionToActive("download-1", QueueState.DOWNLOADING.name)

            assertEquals(1, dao.observeAll().first().count { it.state == QueueState.DOWNLOADING.name })

            // Attempting to transition second item to DOWNLOADING while first is still active must fail
            assertThrows(IllegalStateException::class.java) {
                runBlocking {
                    dao.transitionToActive("download-2", QueueState.DOWNLOADING.name)
                }
            }
        }
    }

    @Test
    fun databaseReopenRestoresState() {
        runBlocking {
            val dbFile = File(context.filesDir, "test_reopen.db")
            dbFile.delete()

            val realDb = Room.databaseBuilder(context, WyStoreDatabase::class.java, dbFile.name)
                .allowMainThreadQueries()
                .build()

            val entity = UpdateQueueEntity(
                id = "persist-1",
                packageName = "persist.app",
                label = "Persist App",
                versionName = "2.0",
                versionCode = 20L,
                source = ManagedSource.GITHUB.name,
                state = QueueState.READY_TO_INSTALL.name,
                priority = 1,
                position = 0
            )

            realDb.updateQueueDao.upsertAtomic(entity)
            realDb.close()

            val reopenedDb = Room.databaseBuilder(context, WyStoreDatabase::class.java, dbFile.name)
                .allowMainThreadQueries()
                .build()

            val loaded = reopenedDb.updateQueueDao.getById("persist-1")
            reopenedDb.close()
            dbFile.delete()

            assertNotNull(loaded)
            assertEquals("persist.app", loaded?.packageName)
            assertEquals(QueueState.READY_TO_INSTALL.name, loaded?.state)
        }
    }
}
