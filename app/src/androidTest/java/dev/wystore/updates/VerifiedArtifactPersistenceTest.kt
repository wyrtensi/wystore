package dev.wystore.updates

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.wystore.data.ArchiveIdentity
import dev.wystore.data.ManagedSource
import dev.wystore.data.local.UpdateQueueEntity
import dev.wystore.data.local.WyStoreDatabase
import dev.wystore.updates.model.QueueAction
import dev.wystore.updates.model.QueueState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * A download only becomes installable if the identity that verification read out of the APK set is
 * stored with the files. Without it the install path has nothing to compare the artifacts against
 * and every install attempt is rejected.
 */
@RunWith(AndroidJUnit4::class)
class VerifiedArtifactPersistenceTest {

    private lateinit var context: Context
    private lateinit var database: WyStoreDatabase
    private lateinit var repository: QueueRepository
    private lateinit var downloadDir: File

    private val identity = ArchiveIdentity(
        packageName = PACKAGE,
        versionName = "3.4.5",
        versionCode = 3405L,
        splitName = null,
        signingDigests = setOf("a".repeat(64), "b".repeat(64))
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, WyStoreDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = QueueRepository(context, database)
        downloadDir = File(context.cacheDir, "verified-artifact-test").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        downloadDir.deleteRecursively()
        File(context.filesDir, "pending_updates").deleteRecursively()
        database.close()
    }

    @Test
    fun readyItemCarriesVerifiedIdentityAndSurvivesReload() = runBlocking {
        val queueId = seedAvailableItem()
        val apk = fakeApk("base.apk")

        // Same order the download worker uses.
        repository.transition(queueId, QueueAction.StartDownload)
        repository.transition(queueId, QueueAction.StartVerification)
        repository.saveVerifiedArtifacts(queueId, identity, listOf(apk), sourceHash = "source-hash")
        repository.transition(queueId, QueueAction.Verified)

        // Read back through the same path the installer uses, not through the in-memory result.
        val pending = repository.getPendingUpdates().single { it.packageName == PACKAGE }
        assertEquals(
            "install-time re-check compares against these digests",
            identity.signingDigests,
            pending.signingDigests
        )
        assertEquals(identity.versionCode, pending.versionCode)
        assertEquals(identity.versionName, pending.versionName)
        assertEquals(1, pending.filePaths.size)
        assertTrue(File(pending.filePaths.single()).isFile)

        val row = database.updateQueueDao.getById(queueId)!!
        assertEquals(QueueState.READY_TO_INSTALL.name, row.state)
        assertEquals(identity.versionCode, row.versionCode)
        assertEquals("source-hash", database.updateQueueDao.getArtifactsForQueue(queueId).single().sourceHash)
    }

    @Test
    fun adoptingTheRealVersionCodeReplacesAConflictingRow() = runBlocking {
        // A previous check already created a row for the version this download turns out to be.
        val stale = UpdateQueueEntity(
            id = "stale-row",
            packageName = PACKAGE,
            label = "Example",
            versionName = "3.4.5",
            versionCode = identity.versionCode,
            source = ManagedSource.RUSTORE.name,
            state = QueueState.AVAILABLE.name
        )
        database.updateQueueDao.insertOrReplace(stale)
        val queueId = seedAvailableItem()

        repository.saveVerifiedArtifacts(queueId, identity, listOf(fakeApk("base.apk")))

        assertNull("the conflicting row must go, not the fresh download", database.updateQueueDao.getById("stale-row"))
        assertNotNull(database.updateQueueDao.getById(queueId))
        assertEquals(identity.versionCode, database.updateQueueDao.getById(queueId)!!.versionCode)
    }

    @Test
    fun aSecondDownloadDropsTheArtifactsOfTheFirst() = runBlocking {
        val queueId = seedAvailableItem()
        repository.saveVerifiedArtifacts(queueId, identity, listOf(fakeApk("base.apk"), fakeApk("split.apk")))
        val firstPaths = database.updateQueueDao.getArtifactsForQueue(queueId).map { it.path }
        assertEquals(2, firstPaths.size)

        repository.saveVerifiedArtifacts(queueId, identity, listOf(fakeApk("only.apk")))

        val remaining = database.updateQueueDao.getArtifactsForQueue(queueId)
        assertEquals(1, remaining.size)
        assertTrue(File(remaining.single().path).isFile)
        assertFalse("orphaned artifact file must be deleted", File(firstPaths.last()).exists())
    }

    @Test
    fun aGitHubRowAdoptsTheRealPackageNameFromTheArchive() = runBlocking {
        // A GitHub row starts with a placeholder because a release file name is not a package name.
        val placeholder = dev.wystore.updates.GitHubInstallScheduler.placeholderPackageName(
            dev.wystore.data.GitHubRepository("wyrtensi", "CapturePort")
        )
        val entity = repository.enqueueAvailableUpdate(
            packageName = placeholder,
            label = "CapturePort.apk",
            versionName = "",
            versionCode = 99L,
            source = ManagedSource.GITHUB
        )

        repository.saveVerifiedArtifacts(entity.id, identity, listOf(fakeApk("base.apk")))

        val row = database.updateQueueDao.getById(entity.id)!!
        assertEquals("the row must carry the package inside the APK", PACKAGE, row.packageName)
        assertEquals(identity.versionCode, row.versionCode)
    }

    @Test
    fun anIdentityWithoutSigningDigestsIsRejected() = runBlocking {
        val queueId = seedAvailableItem()
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.saveVerifiedArtifacts(
                    queueId,
                    identity.copy(signingDigests = emptySet()),
                    listOf(fakeApk("base.apk"))
                )
            }
        }
        assertTrue(database.updateQueueDao.getArtifactsForQueue(queueId).isEmpty())
    }

    @Test
    fun aRetryableFailureLeavesTheItemStartableAgain() = runBlocking {
        val queueId = seedAvailableItem()
        repository.transition(queueId, QueueAction.StartDownload)

        repository.resetForRetry(queueId, dev.wystore.updates.model.QueueErrorCode.NETWORK, "timeout")

        // The retried worker run begins with StartDownload; from FAILED that would throw.
        val restarted = repository.transition(queueId, QueueAction.StartDownload)
        assertEquals(QueueState.DOWNLOADING, restarted.state)
    }

    private suspend fun seedAvailableItem(): String {
        val entity = repository.enqueueAvailableUpdate(
            packageName = PACKAGE,
            label = "Example",
            versionName = "",
            versionCode = 0L,
            source = ManagedSource.RUSTORE
        )
        return entity.id
    }

    private fun fakeApk(name: String): File =
        File(downloadDir, name).apply { writeBytes(ByteArray(64) { it.toByte() }) }

    private companion object {
        const val PACKAGE = "dev.example.verified"
    }
}
