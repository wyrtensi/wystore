package dev.wystore.updates

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import dev.wystore.data.GitHubRepository
import dev.wystore.data.ManagedSource
import dev.wystore.data.local.InstallSessionEntity
import dev.wystore.data.local.UpdateQueueEntity
import dev.wystore.data.local.WyStoreDatabase
import dev.wystore.updates.model.QueueState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class SelfUpdateQueueRetentionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val github = GitHubRepository("wyrtensi", "wystore")

    @Test
    fun lateOlderReleaseDoesNotReplaceTheLatest() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, WyStoreDatabase::class.java).build()
        try {
            val queue = QueueRepository(context, db)
            suspend fun enqueue(version: String, id: Long) = queue.enqueueSelfUpdate(
                context.packageName, "Wy Store", version, id, ManagedSource.GITHUB, github, id
            )
            enqueue("0.6.0", 600)
            val latest = enqueue("0.7.0", 700)
            val late = enqueue("0.6.0", 600)
            assertEquals(latest.id, late.id)
            assertEquals(listOf("0.7.0"), db.updateQueueDao.getByPackage(context.packageName).map { it.versionName })
        } finally {
            db.close()
        }
    }

    @Test
    fun recoveryKeepsActiveInstallAndTransfersExplicitIntent() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, WyStoreDatabase::class.java).build()
        try {
            val dao = db.updateQueueDao
            val queue = QueueRepository(context, db)
            fun row(version: Int, state: QueueState) = UpdateQueueEntity(
                id = UUID.randomUUID().toString(), packageName = context.packageName, label = "Wy Store",
                versionName = "0.$version.0", versionCode = version.toLong(), source = "GITHUB",
                state = state.name, githubRepositoryOwner = github.owner, githubRepositoryName = github.name,
                githubReleaseId = version.toLong()
            )
            val available = row(1, QueueState.AVAILABLE).copy(priority = 10, requiresUpdateOwnership = true)
            val paused = row(2, QueueState.PAUSED).copy(requiresUserConfirmation = true)
            val failed = row(3, QueueState.FAILED)
            val ready = row(4, QueueState.READY_TO_INSTALL)
            val withSession = row(5, QueueState.READY_TO_INSTALL)
            val dialog = row(6, QueueState.AWAITING_USER_CONFIRMATION)
            val latest = row(7, QueueState.AVAILABLE)
            listOf(available, paused, failed, ready, withSession, dialog, latest).forEach { dao.insert(it) }
            dao.insertOrReplaceSession(InstallSessionEntity(withSession.id, context.packageName, 123,
                "test-token", InstallSessionWriter.SESSION_PREPARED))

            queue.discardAllSuperseded()

            val remaining = dao.getByPackage(context.packageName).associateBy { it.id }
            assertEquals(setOf(withSession.id, dialog.id, latest.id), remaining.keys)
            assertTrue(remaining.getValue(latest.id).requiresUpdateOwnership)
            assertTrue(remaining.getValue(latest.id).requiresUserConfirmation)
            assertEquals(10, remaining.getValue(latest.id).priority)
            assertEquals(123, dao.getSession(withSession.id)?.sessionId)
        } finally {
            db.close()
        }
    }
}
