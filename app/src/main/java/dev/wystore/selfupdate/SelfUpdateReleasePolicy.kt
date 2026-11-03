package dev.wystore.selfupdate

import dev.wystore.data.GitHubRelease
import dev.wystore.data.GitHubReleasePolicy

/** Selects a newer, installable stable Wy Store release regardless of publication order. */
object SelfUpdateReleasePolicy {
    private val stableTag = Regex("[vV]?[0-9]+(?:\\.[0-9]+)*")

    fun select(releases: List<GitHubRelease>, currentVersion: String): GitHubRelease? {
        val current = SelfUpdateVersion.parse(currentVersion) ?: return null
        return releases.asSequence()
            .filter { !it.prerelease && stableTag.matches(it.tagName) }
            .filter { GitHubReleasePolicy.apkAssets(it).isNotEmpty() }
            .mapNotNull { release -> SelfUpdateVersion.parse(release.tagName)?.let { release to it } }
            .filter { (_, version) -> SelfUpdateVersion.compare(version, current) > 0 }
            .maxWithOrNull { a, b -> SelfUpdateVersion.compare(a.second, b.second) }
            ?.first
    }
}
