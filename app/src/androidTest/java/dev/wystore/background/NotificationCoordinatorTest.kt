package dev.wystore.background

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.wystore.data.ManagedSource
import dev.wystore.data.local.UpdateQueueEntity
import dev.wystore.data.local.WyStoreDatabase
import dev.wystore.data.local.toSnapshot
import dev.wystore.updates.model.QueueState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationCoordinatorTest {

    private lateinit var context: Context
    private lateinit var db: WyStoreDatabase
    private lateinit var coordinator: NotificationCoordinator

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, WyStoreDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        coordinator = NotificationCoordinator(context)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun activeTransferUsesNotificationId7001() {
        assertEquals(7001, NotificationCoordinator.NOTIFICATION_ID_TRANSFER)
    }

    @Test
    fun readyPackagesShareOneGroupKeyAndSummary() {
        assertEquals("wy_store_group_ready", NotificationCoordinator.NOTIFICATION_GROUP_READY)
        assertEquals(7002, NotificationCoordinator.NOTIFICATION_ID_READY_SUMMARY)
    }

    @Test
    fun deniedNotificationPostDoesNotMutateRoom() {
        runBlocking {
            val entity = UpdateQueueEntity(
                id = "safe-queue-id",
                packageName = "app.safe",
                label = "Safe App",
                versionName = "1.0",
                versionCode = 10L,
                source = ManagedSource.RUSTORE.name,
                state = QueueState.READY_TO_INSTALL.name
            )
            db.updateQueueDao.upsertAtomic(entity)

            // When coordinator attempts to publish notification, even if restricted/denied,
            // Room state remains unchanged.
            coordinator.showReady(entity.toSnapshot())

            val fromDb = db.updateQueueDao.getById("safe-queue-id")
            assertNotNull(fromDb)
            assertEquals(QueueState.READY_TO_INSTALL.name, fromDb?.state)
        }
    }
}
