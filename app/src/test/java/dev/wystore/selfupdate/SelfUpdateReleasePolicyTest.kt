package dev.wystore.selfupdate

import dev.wystore.data.GitHubAsset
import dev.wystore.data.GitHubRelease
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SelfUpdateReleasePolicyTest {
    private fun release(tag: String, prerelease: Boolean = false, apk: Boolean = true) = GitHubRelease(
        id = tag.hashCode().toLong(), tagName = tag, title = tag, description = "", publishedAt = null,
        prerelease = prerelease, assets = if (apk) listOf(GitHubAsset(1, "wystore.apk", 1, "https://github.com/x", null)) else emptyList()
    )

    @Test fun choosesHighestVersionRegardlessOfPublicationOrder() {
        assertEquals("v3.0", SelfUpdateReleasePolicy.select(listOf(release("v2.0"), release("v3.0")), "1.0")?.tagName)
    }

    @Test fun neverOffersPrereleasesOrMalformedTags() {
        assertNull(SelfUpdateReleasePolicy.select(listOf(release("v3.0-rc1", prerelease = true)), "1.0"))
        assertNull(SelfUpdateReleasePolicy.select(listOf(release("v3.0garbage")), "1.0"))
    }

    @Test fun skipsUninstallableAndOlderReleases() {
        assertEquals("v2.0", SelfUpdateReleasePolicy.select(listOf(release("v3.0", apk = false), release("v2.0")), "1.0")?.tagName)
        assertNull(SelfUpdateReleasePolicy.select(listOf(release("v2.0")), "2.0"))
    }
}
