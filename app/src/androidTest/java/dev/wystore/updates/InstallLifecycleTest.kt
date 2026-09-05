package dev.wystore.updates

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.wystore.data.ArchiveIdentity
import dev.wystore.data.ManagedSource
import dev.wystore.data.local.WyStoreDatabase
import dev.wystore.updates.model.QueueAction
import dev.wystore.updates.model.QueueErrorCode
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
import java.io.File

/**
 * The install handover leaves the process, so its durable states have to survive a callback that
 * arrives late, twice, or after a restart. Before this, an install callback was pushed through the
 * reducer from READY_TO_INSTALL and threw an illegal-transition error, losing the result.
 */
@RunWith(AndroidJUnit4::class)
class InstallLifecycleTest {

    private lateinit var context: Context
    private lateinit var database: WyStoreDatabase
    private lateinit var repository: QueueRepository
    private lateinit var workDir: File

    private val identity = ArchiveIdentity(
        packageName = PACKAGE,
        versionName = "2.0",
        versionCode = 200L,
        splitName = null,
        signingDigests = setOf("c".repeat(64))
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, WyStoreDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = QueueRepository(context, database)
        workDir = File(context.cacheDir, "install-lifecycle-test").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        workDir.deleteRecursively()
        File(context.filesDir, "pending_updates").deleteRecursively()
        database.close()
    }

    @Test
    fun aSuccessCallbackIsRecordedEvenThoughTheRowNeverReachedInstalling() = runBlocking {
        val id = seedReadyItem()

        // Exactly what InstallResultReceiver does for STATUS_SUCCESS.
        val result = repository.reconcileInstallResult(id, success = true)

        assertEquals(QueueState.OFFER_NEXT, result?.state)
        assertNull("a finished install must not leave a session behind", database.updateQueueDao.getSession(id))
    }

    @Test
    fun aFailureCallbackRecordsTheReasonRegardlessOfCurrentState() = runBlocking {
        val id = seedReadyItem()

        val result = repository.reconcileInstallResult(
            id = id,
            success = false,
            errorCode = QueueErrorCode.INSTALL_CANCELED,
            errorDetail = "user aborted"
        )

        assertEquals(QueueState.FAILED, result?.state)
        assertEquals(QueueErrorCode.INSTALL_CANCELED, result?.errorCode)
        assertEquals("user aborted", result?.errorDetail)
    }

    @Test
    fun aRedeliveredCallbackDoesNotThrow() = runBlocking {
        val id = seedReadyItem()
        repository.reconcileInstallResult(id, success = true)

        val second = repository.reconcileInstallResult(id, success = true)

        assertEquals(QueueState.OFFER_NEXT, second?.state)
    }

    @Test
    fun theInstallStepsAreGuardedByTheStateTheyRequire() = runBlocking {
        val id = seedReadyItem()

        val confirming = repository.transitionIfIn(
            id, setOf(QueueState.READY_TO_INSTALL), QueueAction.RequestInstallConfirmation
        )
        assertEquals(QueueState.AWAITING_USER_CONFIRMATION, confirming?.state)

        // A second tap (Activity recreated, user pressed again) must be a no-op, not a crash.
        val repeated = repository.transitionIfIn(
            id, setOf(QueueState.READY_TO_INSTALL), QueueAction.RequestInstallConfirmation
        )
        assertEquals(QueueState.AWAITING_USER_CONFIRMATION, repeated?.state)

        val installing = repository.transitionIfIn(
            id, setOf(QueueState.AWAITING_USER_CONFIRMATION), QueueAction.StartInstall
        )
        assertEquals(QueueState.INSTALLING, installing?.state)
    }

    @Test
    fun aFailedHandoverLeavesTheItemInstallableAgain() = runBlocking {
        val id = seedReadyItem()
        repository.transitionIfIn(id, setOf(QueueState.READY_TO_INSTALL), QueueAction.RequestInstallConfirmation)

        val restored = repository.resetReadyToInstall(id)

        assertEquals(QueueState.READY_TO_INSTALL, restored?.state)
        assertTrue(
            "the verified download must still be offered",
            repository.getPendingUpdates().any { it.packageName == PACKAGE }
        )
    }

    @Test
    fun unknownSourcesWaitSurvivesInTheDatabase() = runBlocking {
        seedReadyItem()

        repository.markAwaitingUnknownSources(PACKAGE)

        assertEquals(listOf(PACKAGE), repository.awaitingUnknownSources().map { it.packageName })
        // A parked item still counts as downloaded, otherwise the resumed install cannot find it.
        assertNotNull(repository.getPendingUpdates().firstOrNull { it.packageName == PACKAGE })

        repository.clearAwaitingUnknownSources(PACKAGE)

        assertTrue(repository.awaitingUnknownSources().isEmpty())
        assertEquals(
            QueueState.READY_TO_INSTALL.name,
            database.updateQueueDao.getByPackage(PACKAGE).single().state
        )
    }

    private suspend fun seedReadyItem(): String {
        val entity = repository.enqueueAvailableUpdate(
            packageName = PACKAGE,
            label = "Example",
            versionName = "",
            versionCode = 0L,
            source = ManagedSource.RUSTORE
        )
        repository.transition(entity.id, QueueAction.StartDownload)
        repository.transition(entity.id, QueueAction.StartVerification)
        val apk = File(workDir, "base.apk").apply { writeBytes(ByteArray(32)) }
        repository.saveVerifiedArtifacts(entity.id, identity, listOf(apk))
        repository.transition(entity.id, QueueAction.Verified)
        return entity.id
    }

    private companion object {
        const val PACKAGE = "dev.example.installlifecycle"
    }
}
