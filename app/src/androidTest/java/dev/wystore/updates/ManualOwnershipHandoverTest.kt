package dev.wystore.updates

import android.app.job.JobScheduler
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
import dev.wystore.background.TransferDispatcher
import dev.wystore.data.ManagedSource
import dev.wystore.data.StoreSettings
import dev.wystore.data.local.UpdateArtifactEntity
import dev.wystore.data.local.UpdateQueueEntity
import dev.wystore.data.local.WyStoreDatabase
import dev.wystore.updates.model.QueueState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ManualOwnershipHandoverTest {
    private lateinit var context: Context
    private lateinit var database: WyStoreDatabase
    private lateinit var repository: QueueRepository
    private val packageName = "dev.wystore.test.manual.handover"
    private val queueId = "manual-ownership-handover"
    private var artifact: File? = null

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = WyStoreDatabase.getInstance(context)
        database.clearAllTables()
        repository = QueueRepository.getInstance(context)
        AutoInstallStore(context).clear(packageName)
    }

    @After fun tearDown() {
        TransferDispatcher.cancel(context, queueId)
        AutoInstallStore(context).clear(packageName)
        artifact?.delete()
        database.clearAllTables()
    }

    private fun automatic(state: QueueState) = UpdateQueueEntity(
        id = queueId, packageName = packageName, label = "Test app", versionName = "2",
        versionCode = 2, source = ManagedSource.RUSTORE.name, state = state.name,
        priority = 0, downloadedBytes = 1024, totalBytes = 2048, signingDigests = "digest"
    )

    @Test fun downloadingAutomaticRowBecomesDurableExplicitHandoverWithoutLosingProgress() = runBlocking {
        val original = automatic(QueueState.DOWNLOADING)
        database.updateQueueDao.insert(original)
        val requested = repository.enqueueManualInstall(packageName, "Test app", ManagedSource.RUSTORE,
            requiresUpdateOwnership = true)!!
        assertEquals(queueId, requested.id)
        assertEquals(QueueState.DOWNLOADING.name, requested.state)
        assertEquals(1024L, requested.downloadedBytes)
        assertTrue(requested.requiresUpdateOwnership)
        assertEquals(QueueOrigin.USER_REQUESTED_PRIORITY, requested.priority)
        // The worker can still hold the snapshot it read before the uninstall was confirmed.
        database.updateQueueDao.update(original.copy(downloadedBytes = 1536))
        val saved = database.updateQueueDao.getById(queueId)!!
        assertEquals(1536L, saved.downloadedBytes)
        assertTrue(saved.requiresUpdateOwnership)
        assertEquals(QueueOrigin.USER_REQUESTED_PRIORITY, saved.priority)
        assertFalse(repository.discardAutomatic(queueId))
    }

    @Test fun readyAutomaticRowOffersCachedFileWithoutDispatchingAnotherDownload() = runBlocking {
        val original = automatic(QueueState.READY_TO_INSTALL)
        database.updateQueueDao.insert(original)
        val cached = File.createTempFile("handover-cache", ".apk", context.filesDir).also {
            it.writeText("cached archive bytes")
            artifact = it
        }
        database.updateQueueDao.insertArtifact(UpdateArtifactEntity(
            queueId = queueId, path = cached.absolutePath, size = cached.length()
        ))
        assertEquals(queueId, ManualInstallScheduler.enqueuePrepared(context, packageName, "Test app",
            StoreSettings(autoInstallUpdates = false, autoInstallNewApps = false), requiresUpdateOwnership = true))
        val ready = repository.getPendingUpdates().single()
        assertTrue(ready.userRequested)
        assertTrue(ready.requiresUpdateOwnership)
        assertEquals(listOf(cached.absolutePath), ready.filePaths)
        assertEquals("cached archive bytes", cached.readText())
        assertEquals(QueueState.READY_TO_INSTALL, repository.getById(queueId)!!.state)
        assertTrue(packageName in AutoInstallStore(context).requested())
        assertTrue(WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(TransferDispatcher.downloadWorkName(queueId)).get().isEmpty())
        if (Build.VERSION.SDK_INT >= 34) {
            assertNull(context.getSystemService(JobScheduler::class.java)
                .getPendingJob(TransferDispatcher.jobIdFor(queueId)))
        }
    }

    @Test fun confirmationRequestNotifiesObserverWhenReadyQueueWasAlreadyObserved() = runBlocking {
        val store = AutoInstallStore(context)
        val subscribed = CompletableDeferred<Unit>()
        val changed = async(Dispatchers.Default) {
            withTimeout(5000) {
                store.observeRequests().onEach { subscribed.complete(Unit) }
                    .first { packageName in it }
            }
        }
        subscribed.await()
        store.request(packageName)
        assertTrue(packageName in changed.await())
    }
}
