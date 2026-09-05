package dev.wystore.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cases taken from repositories actually shipped in the catalogue: sing-box attaches OpenWrt
 * `.apk` packages, SmartTube and amneziawg publish notes-only tags with no APK, and LibreTube,
 * Orbot and SimpleX put rolling `nightly`/RC tags above the newest stable one.
 */
class GitHubReleasePolicyTest {

    private fun asset(name: String, size: Long = 1_000) =
        GitHubAsset(id = name.hashCode().toLong(), name = name, sizeBytes = size, downloadUrl = "https://github.com/x", digest = null)

    private fun release(
        tag: String,
        prerelease: Boolean = false,
        assets: List<GitHubAsset> = listOf(asset("app-release.apk"))
    ) = GitHubRelease(
        id = tag.hashCode().toLong(),
        tagName = tag,
        title = tag,
        description = "",
        publishedAt = null,
        prerelease = prerelease,
        assets = assets
    )

    @Test
    fun aRollingNightlyTagIsNotTreatedAsStableEvenWhenNotFlagged() {
        // LibreTube and Metrolist both publish a `nightly` tag that is not flagged as a prerelease.
        val releases = listOf(release("nightly"), release("v32.1"))

        assertEquals("v32.1", GitHubReleasePolicy.selectRelease(releases)?.tagName)
        assertFalse(GitHubReleasePolicy.isStable(release("nightly")))
        assertFalse(GitHubReleasePolicy.isStable(release("17.9.5-RC-4-tor-0.4.9.11")))
        assertFalse(GitHubReleasePolicy.isStable(release("v7.1.0-beta.2")))
        assertTrue(GitHubReleasePolicy.isStable(release("v7.0.2")))
        assertTrue(GitHubReleasePolicy.isStable(release("2.3.7")))
    }

    @Test
    fun aReleaseWithNoApkIsSkippedRatherThanChosen() {
        // amneziawg v3.0.1 and SmartTube's `notification2` tag carry no APK at all.
        val releases = listOf(
            release("notification2", assets = emptyList()),
            release("32.38s", assets = listOf(asset("SmartTube_stable_32.38.apk")))
        )

        assertEquals("32.38s", GitHubReleasePolicy.selectRelease(releases)?.tagName)
    }

    @Test
    fun everythingBeingAPrereleaseStillYieldsAnInstall() {
        // Cromite's recent tags are all betas; the previous code threw NoSuchElementException here.
        val releases = listOf(release("v151_beta", prerelease = true), release("v150_beta", prerelease = true))

        assertEquals("v151_beta", GitHubReleasePolicy.selectRelease(releases)?.tagName)
    }

    @Test
    fun nothingInstallableYieldsNullInsteadOfThrowing() {
        val releases = listOf(release("notes-only", assets = emptyList()))

        assertNull(GitHubReleasePolicy.selectRelease(releases))
        assertNull(GitHubReleasePolicy.selectRelease(emptyList()))
    }

    @Test
    fun anAssetFilterKeepsOpenWrtPackagesOutOfTheInstallList() {
        val singBox = release(
            "v1.14.0",
            assets = listOf(
                asset("sing-box-1.14.0-x86_64.apk"),
                asset("sing-box-1.14.0-aarch64.apk"),
                asset("SFA-1.14.0-universal.apk"),
                asset("SFA-1.14.0-arm64-v8a.apk")
            )
        )
        val pattern = Regex("^SFA-", RegexOption.IGNORE_CASE)

        val assets = GitHubReleasePolicy.apkAssets(singBox, pattern)

        assertEquals(
            listOf("SFA-1.14.0-universal.apk", "SFA-1.14.0-arm64-v8a.apk"),
            assets.map { it.name }
        )
    }

    @Test
    fun theDeviceAbiBuildIsPreferredOverTheUniversalOne() {
        val assets = listOf(
            asset("SFA-1.14.0-universal.apk", size = 121_000_000),
            asset("SFA-1.14.0-arm64-v8a.apk", size = 30_000_000),
            asset("SFA-1.14.0-armeabi-v7a.apk", size = 28_000_000)
        )

        val chosen = GitHubReleasePolicy.preferredAsset(assets, supportedAbis = listOf("arm64-v8a", "armeabi-v7a"))

        assertEquals("SFA-1.14.0-arm64-v8a.apk", chosen?.name)
    }

    @Test
    fun anUnrecognisableAssetSetFallsBackToTheSmallestBuild() {
        val assets = listOf(asset("big.apk", size = 300_000_000), asset("small.apk", size = 5_000_000))

        assertEquals("small.apk", GitHubReleasePolicy.preferredAsset(assets)?.name)
        assertNull(GitHubReleasePolicy.preferredAsset(emptyList()))
    }

    @Test
    fun aSingleAssetIsUsedWhateverItIsNamed() {
        val only = listOf(asset("ByeByeDPI-v1.7.8-universal-release.apk"))

        assertEquals(only.single(), GitHubReleasePolicy.preferredAsset(only, listOf("arm64-v8a")))
    }
}
