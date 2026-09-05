package dev.wystore.updates

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.wystore.data.ManagedSource
import dev.wystore.data.local.UpdateQueueEntity
import dev.wystore.data.local.WyStoreDatabase
import dev.wystore.updates.model.QueueAction
import dev.wystore.updates.model.QueueState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QueueRecoveryTest {

    private lateinit var context: Context
    private lateinit var database: WyStoreDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = WyStoreDatabase.getInstance(context)
        database.clearAllTables()
    }

    @After
    fun tearDown() {
        database.clearAllTables()
    }

    @Test
    fun queueItemsSurviveRecreationAndPreserveOrdering(): Unit {
        runBlocking {
            val entity1 = UpdateQueueEntity(
                id = "item-alpha",
                packageName = "app.alpha",
                label = "Alpha App",
                versionName = "1.0",
                versionCode = 10,
                source = ManagedSource.RUSTORE.name,
                state = QueueState.READY_TO_INSTALL.name,
                priority = 10,
                position = 0,
                downloadedBytes = 1024,
                totalBytes = 1024
            )
            val entity2 = UpdateQueueEntity(
                id = "item-beta",
                packageName = "app.beta",
                label = "Beta App",
                versionName = "2.0",
                versionCode = 20,
                source = ManagedSource.RUSTORE.name,
                state = QueueState.AVAILABLE.name,
                priority = 5,
                position = 1
            )
            database.updateQueueDao.upsertAtomic(entity1)
            database.updateQueueDao.upsertAtomic(entity2)

            // Recreate coordinator and repository
            val freshRepo = QueueRepository.getInstance(context)
            val coordinator = QueueCoordinator(context, repository = freshRepo)

            val items = coordinator.observeAll().first()
            assertEquals(2, items.size)
            assertEquals("item-alpha", items[0].id)
            assertEquals(QueueState.READY_TO_INSTALL, items[0].state)
            assertEquals("item-beta", items[1].id)
            assertEquals(QueueState.AVAILABLE, items[1].state)

            // Move item1 to AWAITING_UNKNOWN_SOURCES_PERMISSION and verify survival
            freshRepo.transition("item-alpha", QueueAction.AwaitUnknownSourcesPermission)
            val item1After = freshRepo.getById("item-alpha")
            assertNotNull(item1After)
            assertEquals(QueueState.AWAITING_UNKNOWN_SOURCES_PERMISSION, item1After?.state)

            // Move item1 to INSTALLING and verify survival
            freshRepo.transition("item-alpha", QueueAction.UnknownSourcesPermissionGranted)
            freshRepo.transition("item-alpha", QueueAction.StartInstall)
            val item1Installing = freshRepo.getById("item-alpha")
            assertNotNull(item1Installing)
            assertEquals(QueueState.INSTALLING, item1Installing?.state)
        }
    }
}
