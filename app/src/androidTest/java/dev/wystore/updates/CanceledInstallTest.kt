package dev.wystore.updates

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.wystore.data.ManagedSource
import dev.wystore.data.local.UpdateArtifactEntity
import dev.wystore.data.local.UpdateQueueEntity
import dev.wystore.data.local.WyStoreDatabase
import dev.wystore.updates.model.QueueErrorCode
import dev.wystore.updates.model.QueueState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Declining Android's install dialog does not throw the download away.
 *
 * The outcome used to be recorded as a plain failure, so a 200 MB APK that was already downloaded
 * and verified sat behind a "Retry" button that fetched all of it again.
 */
@RunWith(AndroidJUnit4::class)
class CanceledInstallTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: WyStoreDatabase
    private lateinit var repository: QueueRepository
    private lateinit var artifact: File

    @Before
    fun setUp() {
        database = WyStoreDatabase.getInstance(context)
        repository = QueueRepository.getInstance(context)
        artifact = File(context.cacheDir, "canceled-install-test.apk").apply { writeBytes(ByteArray(64)) }
    }

    @After
    fun tearDown() {
        runBlocking { database.updateQueueDao.deleteById(ID) }
        artifact.delete()
    }

    @Test
    fun aDeclinedInstallStaysInstallable() = runBlocking {
        seedInstallingRow()

        val result = repository.reconcileInstallResult(
            id = ID,
            success = false,
            errorCode = QueueErrorCode.INSTALL_CANCELED,
            errorDetail = "user declined"
        )

        assertEquals(QueueState.READY_TO_INSTALL, result?.state)
        assertEquals(null, result?.errorCode)
    }

    @Test
    fun anInstallThatActuallyFailedIsStillAFailure() = runBlocking {
        seedInstallingRow()

        val result = repository.reconcileInstallResult(
            id = ID,
            success = false,
            errorCode = QueueErrorCode.INSTALL_FAILED,
            errorDetail = "installer said no"
        )

        assertEquals(QueueState.FAILED, result?.state)
    }

    private suspend fun seedInstallingRow() {
        database.updateQueueDao.deleteById(ID)
        database.updateQueueDao.insertOrReplace(
            UpdateQueueEntity(
                id = ID,
                packageName = "dev.wystore.canceled.test",
                label = "Canceled install",
                versionName = "1.0",
                versionCode = 1,
                source = ManagedSource.RUSTORE.name,
                state = QueueState.INSTALLING.name,
                priority = 0,
                position = 0,
                signingDigests = "abc"
            )
        )
        database.updateQueueDao.insertArtifacts(
            listOf(
                UpdateArtifactEntity(
                    queueId = ID,
                    path = artifact.absolutePath,
                    size = artifact.length()
                )
            )
        )
    }

    private companion object {
        const val ID = "canceled-install-test"
    }
}
