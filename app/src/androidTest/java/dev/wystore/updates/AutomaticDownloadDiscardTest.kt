package dev.wystore.updates

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.wystore.data.ManagedSource
import dev.wystore.data.local.WyStoreDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutomaticDownloadDiscardTest {
    @Test
    fun aStaleLabelUpdateCannotEraseAManualRequestBeforeAutomaticCleanup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, WyStoreDatabase::class.java).build()
        try {
            val queue = QueueRepository(context, database)
            val stale = queue.enqueueAvailableUpdate("example.app", "Example", "2.0", 20, ManagedSource.RUSTORE)
            queue.markUserRequested(stale.id)
            database.updateQueueDao.update(stale.copy(label = "Refreshed source label"))
            assertEquals(QueueOrigin.USER_REQUESTED_PRIORITY, queue.getEntityById(stale.id)!!.priority)
            assertFalse(queue.discardAutomatic(stale.id))
            assertNotNull(queue.getById(stale.id))
        } finally {
            database.close()
        }
    }

    @Test
    fun replacingOrUpsertingAStaleRowCannotEraseAnExplicitRequest() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, WyStoreDatabase::class.java).build()
        try {
            val queue = QueueRepository(context, database)
            val stale = queue.enqueueAvailableUpdate("example.app", "Example", "2.0", 20, ManagedSource.RUSTORE)
            queue.markUserRequested(stale.id)
            database.updateQueueDao.upsertAtomic(stale.copy(label = "From the next check"))
            assertEquals(QueueOrigin.USER_REQUESTED_PRIORITY, queue.getEntityById(stale.id)!!.priority)
            database.updateQueueDao.insertOrReplace(stale.copy(id = "replacement-row"))
            assertEquals(QueueOrigin.USER_REQUESTED_PRIORITY, queue.getEntityById("replacement-row")!!.priority)
            assertFalse(queue.discardAutomatic("replacement-row"))
        } finally {
            database.close()
        }
    }

    @Test
    fun aManualRequestMadeAfterTheCheckSurvivesAutomaticCleanup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, WyStoreDatabase::class.java).build()
        try {
            val queue = QueueRepository(context, database)
            val row = queue.enqueueAvailableUpdate("example.app", "Example", "2.0", 20, ManagedSource.RUSTORE)
            queue.markUserRequested(row.id)
            assertFalse(queue.discardAutomatic(row.id))
            assertNotNull(queue.getById(row.id))
        } finally {
            database.close()
        }
    }

    @Test
    fun anObsoleteAutomaticRequestIsRemoved() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, WyStoreDatabase::class.java).build()
        try {
            val queue = QueueRepository(context, database)
            val row = queue.enqueueAvailableUpdate("example.app", "Example", "2.0", 20, ManagedSource.RUSTORE)
            assertTrue(queue.discardAutomatic(row.id))
            assertNull(queue.getById(row.id))
        } finally {
            database.close()
        }
    }
}
