package dev.wystore.updates

import dev.wystore.data.GitHubAsset
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import dev.wystore.data.TransferFailure
import dev.wystore.updates.model.QueueErrorCode
import kotlinx.coroutines.runBlocking

class GitHubAssetChoiceTest {
    private val arm64 = GitHubAsset(1, "app-arm64-v8a.apk", 10, "https://github.com/o/r/releases/download/v1/a.apk", null)
    private val universal = GitHubAsset(2, "app-universal.apk", 30, "https://github.com/o/r/releases/download/v1/u.apk", null)

    @Test
    fun aNewManualChoiceUpdatesAnExistingHandoversExactAsset() {
        assertTrue(GitHubAssetChoice.shouldRememberExact(false, true, automatic = false))
        assertFalse(GitHubAssetChoice.shouldRememberExact(false, true, automatic = true))
        assertFalse(GitHubAssetChoice.shouldRememberExact(false, false, automatic = false))
        assertTrue(GitHubAssetChoice.shouldRememberExact(true, false, automatic = false))
    }

    @Test
    fun thePickedFileWins() {
        // The ABI-preferred build used to be fetched whatever was tapped.
        assertEquals(universal, GitHubAssetChoice.pick(listOf(arm64, universal), 2) { arm64 })
    }

    @Test
    fun withoutAChoiceTheFallbackDecides() {
        assertEquals(arm64, GitHubAssetChoice.pick(listOf(arm64, universal), null) { arm64 })
    }

    @Test
    fun aFileTheReleaseNoLongerHasFallsBack() {
        assertEquals(arm64, GitHubAssetChoice.pick(listOf(arm64), 2) { arm64 })
    }

    @Test
    fun aHandoverDownloadsTheSavedAssetWithoutResolvingAnotherRelease() = runBlocking {
        var resolvedAnotherRelease = false
        val selected = GitHubAssetChoice.forDownload(true, universal) {
            resolvedAnotherRelease = true
            arm64
        }

        assertEquals(universal, selected)
        assertFalse(resolvedAnotherRelease)
    }

    @Test
    fun aHandoverWithNoSavedAssetFailsWithoutFallingBack() {
        var resolvedAnotherRelease = false
        val failure = assertThrows(TransferFailure::class.java) {
            runBlocking {
                GitHubAssetChoice.forDownload(true, null) {
                    resolvedAnotherRelease = true
                    arm64
                }
            }
        }

        assertEquals(QueueErrorCode.SOURCE_CHANGED, failure.code)
        assertFalse(resolvedAnotherRelease)
    }
}
