package dev.wystore.updates

import dev.wystore.data.GitHubAsset
import org.junit.Assert.assertEquals
import org.junit.Test

class GitHubAssetChoiceTest {
    private val arm64 = GitHubAsset(1, "app-arm64-v8a.apk", 10, "https://github.com/o/r/releases/download/v1/a.apk", null)
    private val universal = GitHubAsset(2, "app-universal.apk", 30, "https://github.com/o/r/releases/download/v1/u.apk", null)

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
}
