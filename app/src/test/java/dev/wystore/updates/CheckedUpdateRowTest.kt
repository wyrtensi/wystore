package dev.wystore.updates

import dev.wystore.data.local.UpdateQueueEntity
import dev.wystore.updates.model.QueueState
import org.junit.Assert.assertEquals
import org.junit.Test

class CheckedUpdateRowTest {

    private val downloaded = UpdateQueueEntity(
        id = "row",
        packageName = "ru.app",
        label = "App",
        versionName = "2.0",
        versionCode = 20,
        source = "RUSTORE",
        state = QueueState.READY_TO_INSTALL.name,
        priority = QueueOrigin.USER_REQUESTED_PRIORITY,
        position = 3,
        downloadedBytes = 500,
        totalBytes = 500,
        errorCode = "SIGNATURE",
        errorDetail = "SOURCE_FINGERPRINT_MISMATCH",
        signingDigests = "abc",
        createdAt = 1,
        updatedAt = 2
    )

    private fun check(existing: UpdateQueueEntity?, priority: Int = 0) = CheckedUpdateRow.merge(
        existing = existing,
        newId = "new",
        packageName = "ru.app",
        label = "App 2",
        versionName = "2.0",
        versionCode = 20,
        source = "RUSTORE",
        priority = priority,
        githubRepositoryOwner = null,
        githubRepositoryName = null,
        githubReleaseId = null,
        now = 100
    )

    @Test
    fun aCheckKeepsWhatTheDownloadVerified() {
        val merged = check(downloaded)
        // The verified identity is what install time re-checks against; losing it threw the APK away.
        assertEquals("abc", merged.signingDigests)
        assertEquals(QueueState.READY_TO_INSTALL.name, merged.state)
        assertEquals(500L, merged.downloadedBytes)
        assertEquals("SIGNATURE", merged.errorCode)
        assertEquals("SOURCE_FINGERPRINT_MISMATCH", merged.errorDetail)
        assertEquals(3, merged.position)
        assertEquals(1L, merged.createdAt)
        assertEquals("row", merged.id)
        assertEquals("App 2", merged.label)
    }

    @Test
    fun aCheckNeverDemotesARowSomeoneAskedFor() {
        assertEquals(QueueOrigin.USER_REQUESTED_PRIORITY, check(downloaded, priority = 0).priority)
        assertEquals(QueueOrigin.USER_REQUESTED_PRIORITY, check(downloaded.copy(priority = 0), priority = 10).priority)
    }

    @Test
    fun aManualRowLearnsItsVersionFromTheCheck() {
        val merged = check(downloaded.copy(versionCode = 0, state = QueueState.AVAILABLE.name))
        assertEquals(20L, merged.versionCode)
    }

    @Test
    fun aNewUpdateStartsWaiting() {
        val merged = check(null, priority = QueueOrigin.USER_VISIBLE_PRIORITY)
        assertEquals("new", merged.id)
        assertEquals(QueueState.AVAILABLE.name, merged.state)
        assertEquals(QueueOrigin.USER_VISIBLE_PRIORITY, merged.priority)
        assertEquals("", merged.signingDigests)
        assertEquals(100L, merged.createdAt)
    }

    @Test
    fun aDownloadedGitHubRowIsFoundByItsRelease() {
        // The check names a GitHub row by its release id; verification then writes the APK's real
        // version over it. Looked up by version alone, the row was missed and the release fetched
        // again on every check.
        val verified = downloaded.copy(source = "GITHUB", versionCode = 412, githubReleaseId = 98765)
        val found = CheckedUpdateRow.existing(listOf(verified), versionCode = 98765, source = "GITHUB", githubReleaseId = 98765)
        assertEquals("row", found?.id)
    }

    @Test
    fun anExactVersionWinsOverOtherRows() {
        val old = downloaded.copy(id = "old", versionCode = 10)
        val current = downloaded.copy(id = "current", versionCode = 20)
        assertEquals("current", CheckedUpdateRow.existing(listOf(old, current), 20, "RUSTORE", null)?.id)
    }

    @Test
    fun aManualRowWithoutAVersionIsReused() {
        val manual = downloaded.copy(id = "manual", versionCode = 0, state = QueueState.AVAILABLE.name)
        assertEquals("manual", CheckedUpdateRow.existing(listOf(manual), 20, "RUSTORE", null)?.id)
    }

    @Test
    fun anotherSourceIsNotTheSameRow() {
        assertEquals(null, CheckedUpdateRow.existing(listOf(downloaded), 20, "GITHUB", null)?.id)
    }
}
