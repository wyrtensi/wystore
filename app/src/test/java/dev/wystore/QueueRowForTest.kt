package dev.wystore

import dev.wystore.data.GitHubRepository
import dev.wystore.updates.QueueRecoveryPolicy
import dev.wystore.updates.model.QueueState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Updates a background check found were downloading or waiting, and the app's card still showed
 * "Open": the first row for the package in queue order was an old, finished one.
 */
class QueueRowForTest {

    private fun row(id: String, status: InstallQueueStatus, packageName: String = "ru.app") =
        InstallQueueItem(id = id, packageName = packageName, label = id, status = status)

    @Test
    fun `the row being worked on wins over finished history in front of it`() {
        val queue = listOf(
            row("old-finished", InstallQueueStatus.COMPLETE),
            row("old-canceled", InstallQueueStatus.CANCELED),
            row("found-by-check", InstallQueueStatus.QUEUED)
        )
        assertEquals("found-by-check", queue.rowFor("ru.app")?.id)
    }

    @Test
    fun `a downloaded row and a paused one count as current`() {
        assertEquals("ready", listOf(row("done", InstallQueueStatus.COMPLETE), row("ready", InstallQueueStatus.READY)).rowFor("ru.app")?.id)
        assertEquals("paused", listOf(row("done", InstallQueueStatus.COMPLETE), row("paused", InstallQueueStatus.PAUSED)).rowFor("ru.app")?.id)
    }

    @Test
    fun `a failure outranks history, and history is still returned when that is all there is`() {
        assertEquals("failed", listOf(row("done", InstallQueueStatus.COMPLETE), row("failed", InstallQueueStatus.FAILED)).rowFor("ru.app")?.id)
        assertEquals("done", listOf(row("done", InstallQueueStatus.COMPLETE)).rowFor("ru.app")?.id)
        assertNull(listOf(row("other", InstallQueueStatus.QUEUED, "ru.other")).rowFor("ru.app"))
    }

    @Test
    fun `an install in progress outranks the next version downloading beside it`() {
        // Downloads and installs run side by side now, so one app can have both at once; which
        // one the card showed depended on the order Room returned them in.
        val queue = listOf(row("next", InstallQueueStatus.DOWNLOADING), row("current", InstallQueueStatus.INSTALLING))
        assertEquals("current", queue.rowFor("ru.app")?.id)
        assertEquals("current", queue.reversed().rowFor("ru.app")?.id)
    }

    private val repo = GitHubRepository("owner", "app")

    private fun githubRow(id: String, status: InstallQueueStatus, repository: GitHubRepository = repo) =
        InstallQueueItem(id = id, packageName = "pending.github.$id", label = id, status = status, githubRepository = repository)

    @Test
    fun `the GitHub screen shows the open repository's install while it runs`() {
        val queue = listOf(
            githubRow("done", InstallQueueStatus.COMPLETE),
            githubRow("other", InstallQueueStatus.DOWNLOADING, GitHubRepository("owner", "other")),
            githubRow("running", InstallQueueStatus.VERIFYING)
        )
        assertEquals("running", queue.githubRowFor(repo)?.id)
    }

    @Test
    fun `a finished or canceled GitHub install leaves no card behind`() {
        // A card that said "Queued - please do not close Wy Store" stayed for the life of the
        // process after the install had long finished.
        assertNull(listOf(githubRow("done", InstallQueueStatus.COMPLETE)).githubRowFor(repo))
        assertNull(listOf(githubRow("canceled", InstallQueueStatus.CANCELED)).githubRowFor(repo))
        assertNull(listOf(githubRow("running", InstallQueueStatus.DOWNLOADING)).githubRowFor(null))
        assertNull(listOf(githubRow("other", InstallQueueStatus.DOWNLOADING, GitHubRepository("owner", "other"))).githubRowFor(repo))
    }

    @Test
    fun `a failed GitHub install is shown, a downloaded one waiting on Updates is not`() {
        assertEquals("failed", listOf(githubRow("failed", InstallQueueStatus.FAILED)).githubRowFor(repo)?.id)
        assertNull(listOf(githubRow("ready", InstallQueueStatus.READY)).githubRowFor(repo))
        assertNull(listOf(githubRow("paused", InstallQueueStatus.PAUSED)).githubRowFor(repo))
    }

    @Test
    fun `a row left in flight with nothing scheduled for it is orphaned`() {
        assertTrue(QueueRecoveryPolicy.isOrphaned(QueueState.DOWNLOADING, workScheduled = false, jobScheduled = false))
        assertFalse(QueueRecoveryPolicy.isOrphaned(QueueState.DOWNLOADING, workScheduled = true, jobScheduled = false))
        assertFalse(QueueRecoveryPolicy.isOrphaned(QueueState.DOWNLOADING, workScheduled = false, jobScheduled = true))
        assertFalse(QueueRecoveryPolicy.isOrphaned(QueueState.PAUSED, workScheduled = false, jobScheduled = false))
        assertFalse(QueueRecoveryPolicy.isOrphaned(QueueState.READY_TO_INSTALL, workScheduled = false, jobScheduled = false))
    }
}
