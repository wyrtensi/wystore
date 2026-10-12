package dev.wystore.updates

import dev.wystore.data.local.UpdateQueueEntity
import dev.wystore.updates.model.QueueState
import org.junit.Assert.assertEquals
import org.junit.Test

class SupersededDownloadsTest {

    private fun row(
        id: String,
        versionCode: Long,
        state: QueueState = QueueState.READY_TO_INSTALL,
        packageName: String = "ru.app",
        source: String = "RUSTORE",
        updatedAt: Long = 1
    ) = UpdateQueueEntity(
        id = id,
        packageName = packageName,
        label = "App",
        versionName = "$versionCode",
        versionCode = versionCode,
        source = source,
        state = state.name,
        priority = 0,
        position = 0,
        createdAt = 1,
        updatedAt = updatedAt
    )

    private fun ids(rows: List<UpdateQueueEntity>) = rows.map { it.id }.toSet()

    @Test
    fun anOlderDownloadGoesWhenANewerOneIsReady() {
        // Two downloaded versions of one app gave the Updates list two items with the same key,
        // and opening the tab crashed the app.
        val rows = listOf(row("old", 20), row("new", 21))
        assertEquals(setOf("old"), ids(SupersededDownloads.superseded(rows)))
    }

    @Test
    fun theSameVersionFromAnotherSourceKeepsOnlyTheLatest() {
        val rows = listOf(
            row("rustore", 21, source = "RUSTORE", updatedAt = 1),
            row("github", 21, source = "GITHUB", updatedAt = 2)
        )
        assertEquals(setOf("rustore"), ids(SupersededDownloads.superseded(rows)))
    }

    @Test
    fun aDownloadInTheConfirmationDialogIsNeverRemoved() {
        val rows = listOf(row("dialog", 20, QueueState.AWAITING_USER_CONFIRMATION), row("new", 21))
        assertEquals(emptySet<String>(), ids(SupersededDownloads.superseded(rows)))
    }

    @Test
    fun aRowWaitingForTheDialogWinsATie() {
        val rows = listOf(
            row("dialog", 21, QueueState.AWAITING_USER_CONFIRMATION, updatedAt = 1),
            row("ready", 21, source = "GITHUB", updatedAt = 2)
        )
        assertEquals(setOf("ready"), ids(SupersededDownloads.superseded(rows)))
    }

    @Test
    fun rowsThatHoldNoDownloadAreLeftAlone() {
        val rows = listOf(
            row("waiting", 22, QueueState.AVAILABLE),
            row("downloading", 23, QueueState.DOWNLOADING),
            row("failed", 19, QueueState.FAILED),
            row("ready", 21)
        )
        assertEquals(emptySet<String>(), ids(SupersededDownloads.superseded(rows)))
    }

    @Test
    fun theRowJustVerifiedStaysEvenWhenANewerOneIsThere() {
        // The worker goes on to install the row it verified; deleting it under the worker would
        // fail that step instead.
        val rows = listOf(row("verified", 20), row("newer", 21))
        assertEquals(emptySet<String>(), ids(SupersededDownloads.superseded(rows, keepId = "verified")))
    }

    @Test
    fun eachPackageIsJudgedOnItsOwn() {
        val rows = listOf(
            row("a-old", 20, packageName = "ru.a"),
            row("a-new", 21, packageName = "ru.a"),
            row("b", 5, packageName = "ru.b")
        )
        assertEquals(setOf("a-old"), ids(SupersededDownloads.superseded(rows)))
    }
}
