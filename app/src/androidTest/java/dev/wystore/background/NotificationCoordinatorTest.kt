package dev.wystore.background

import android.app.NotificationManager
import android.content.Context
import android.os.Build
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    /**
     * Ready updates are one notification, not one per app plus a summary. The ids also have to stay
     * distinct from each other: the RuStore compatibility worker and the check summary both used
     * 7004, so whichever posted second silently replaced the other.
     */
    @Test
    fun everyNotificationIdIsDistinct() {
        val ids = listOf(
            NotificationCoordinator.NOTIFICATION_ID_TRANSFER,
            NotificationCoordinator.NOTIFICATION_ID_READY_SUMMARY,
            NotificationCoordinator.NOTIFICATION_ID_ERRORS,
            NotificationCoordinator.NOTIFICATION_ID_CHECK_SUMMARY
        )

        assertEquals("ids must not collide: $ids", ids.size, ids.toSet().size)
    }

    @Test
    fun channelsExistWithTheImportanceTheyWereGiven() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)

        val ready = manager.getNotificationChannel(NotificationCoordinator.CHANNEL_READY)
        assertNotNull("the ready channel must be created up front", ready)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, ready.importance)

        // Errors deserve to be noticed but not to interrupt: they used to be IMPORTANCE_HIGH,
        // which produces a heads-up banner for a download that can wait.
        val errors = manager.getNotificationChannel(NotificationCoordinator.CHANNEL_ERRORS)
        assertNotNull(errors)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, errors.importance)

        val checks = manager.getNotificationChannel(NotificationCoordinator.CHANNEL_CHECKS)
        assertNotNull(checks)
        assertTrue("periodic check reports must not compete for attention", checks.importance <= NotificationManager.IMPORTANCE_LOW)
    }

    @Test
    fun theSupersededChannelsAreRemoved() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)

        // Android fixes a channel's importance when it is first created and ignores later changes,
        // so the rebalanced channels carry new ids and the originals have to go.
        listOf(
            "wy_store_update_checks",
            "wy_store_active_transfers",
            "wy_store_ready_updates",
            "wy_store_update_errors"
        ).forEach { legacyId ->
            assertNull("$legacyId should have been deleted", manager.getNotificationChannel(legacyId))
        }
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

            // Publishing may be refused outright on Android 13+ without the runtime permission.
            // Whatever the system does with it, the queue row must be untouched.
            coordinator.publishReady(listOf(entity.toSnapshot()))

            val fromDb = db.updateQueueDao.getById("safe-queue-id")
            assertNotNull(fromDb)
            assertEquals(QueueState.READY_TO_INSTALL.name, fromDb?.state)
        }
    }

    @Test
    fun anEmptyReadySetIsSafeToPublish() {
        // This is how the last pending update clears the shade after it is installed.
        coordinator.publishReady(emptyList())
        coordinator.publishErrors(emptyList())
    }
}
