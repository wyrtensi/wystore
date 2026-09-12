package dev.wystore.updates

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.wystore.R
import dev.wystore.background.TransferDispatcher
import dev.wystore.data.ManagedSource
import dev.wystore.data.local.UpdateArtifactEntity
import dev.wystore.data.local.UpdateQueueEntity
import dev.wystore.data.local.WyStoreDatabase
import dev.wystore.updates.model.QueueErrorCode
import dev.wystore.updates.model.QueueState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Issue #2, from the side of a device that has not learnt anything yet.
 *
 * On MIUI with optimisation on, the first session this store commits comes back refused. That
 * install must end up in front of the system installer, not in an error the user has to decode.
 */
@RunWith(AndroidJUnit4::class)
class FirmwareInstallFallbackTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs = context.getSharedPreferences("wystore_installer", Context.MODE_PRIVATE)
    private lateinit var database: WyStoreDatabase
    private lateinit var repository: QueueRepository
    private val artifacts = mutableListOf<File>()

    @Before
    fun setUp() {
        database = WyStoreDatabase.getInstance(context)
        repository = QueueRepository.getInstance(context)
        prefs.edit().clear().commit()
    }

    @After
    fun tearDown() {
        runBlocking {
            TransferDispatcher.cancel(context, ID)
            database.updateQueueDao.deleteById(ID)
        }
        AutoInstallStore(context).clear(PACKAGE)
        artifacts.forEach { it.delete() }
        prefs.edit().clear().commit()
    }

    @Test
    fun aRefusedSingleApkGoesBackToReadyWithARequestToInstall() = runBlocking {
        seedInstallingRow(artifactCount = 1)

        FirmwareInstallFallback.afterRefusal(context, ID, "INSTALL_FAILED_INTERNAL_ERROR: Permission Denied")

        val row = repository.getById(ID)
        assertEquals(QueueState.READY_TO_INSTALL, row?.state)
        assertEquals(null, row?.errorCode)
        assertTrue("the device must remember the refusal", SessionInstallSupport(context).sessionsRefused())
        assertTrue(
            "the open app installs whatever is requested, and that is what re-hands it",
            PACKAGE in AutoInstallStore(context).requested()
        )
    }

    @Test
    fun splitPartsAreFetchedAgainOnceAndThenReportedHonestly() = runBlocking {
        seedInstallingRow(artifactCount = 3)
        SessionInstallSupport(context).markSessionsRefused()

        FirmwareInstallFallback.refetchWhole(context, ID)
        TransferDispatcher.cancel(context, ID)
        assertNotEquals(
            "the first time is a download, not an install failure",
            QueueErrorCode.INSTALL_FAILED,
            repository.getById(ID)?.errorCode
        )
        assertTrue(PACKAGE in AutoInstallStore(context).requested())

        // The same version arriving in parts again must not become a download loop.
        FirmwareInstallFallback.refetchWhole(context, ID)
        val row = repository.getById(ID)
        assertEquals(QueueState.FAILED, row?.state)
        assertEquals(QueueErrorCode.INSTALL_FAILED, row?.errorCode)
        assertEquals(context.getString(R.string.msg_install_split_refused), row?.errorDetail)
    }

    private suspend fun seedInstallingRow(artifactCount: Int) {
        database.updateQueueDao.deleteById(ID)
        database.updateQueueDao.insertOrReplace(
            UpdateQueueEntity(
                id = ID,
                packageName = PACKAGE,
                label = "Firmware fallback",
                versionName = "1.0",
                versionCode = 1,
                source = ManagedSource.RUSTORE.name,
                state = QueueState.INSTALLING.name,
                priority = 0,
                position = 0,
                signingDigests = "abc"
            )
        )
        val files = (0 until artifactCount).map { index ->
            File(context.cacheDir, "firmware-fallback-$index.apk").apply { writeBytes(ByteArray(64)) }
        }
        artifacts += files
        database.updateQueueDao.insertArtifacts(
            files.map { UpdateArtifactEntity(queueId = ID, path = it.absolutePath, size = it.length()) }
        )
    }

    private companion object {
        const val ID = "firmware-fallback-test"
        const val PACKAGE = "dev.wystore.firmware.fallback.test"
    }
}
