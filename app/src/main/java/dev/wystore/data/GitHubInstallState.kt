package dev.wystore.data

import dev.wystore.selfupdate.SelfUpdateVersion

/** Where a GitHub-hosted app stands against the release the page is showing. */
enum class GitHubInstallState {
    /** Nothing of it on the device. */
    NotInstalled,

    /** The release is genuinely newer than what is installed. */
    UpdateAvailable,

    /** What is installed is this release, or newer. */
    Current,

    /** Installed, but the two version strings cannot be compared. */
    Unknown
}

/**
 * Decides what a GitHub app page should offer.
 *
 * The page used to choose by "is it installed at all", so an app already running the release on
 * screen was still offered "Update" — and pressing it downloaded and reinstalled the same build.
 * A RuStore card has never behaved that way, and there is no reason this one should.
 *
 * Version names are the only thing both sides state: a release id is not a version code, and the
 * tag is what a repository actually publishes. The same comparison Wy Store uses on itself is
 * reused here, decoration and all — "v1.2.3", "1.2.3-rc1" and "1.2.3" are one version.
 */
object GitHubInstallStatePolicy {

    fun stateFor(installedVersionName: String?, releaseTag: String?): GitHubInstallState {
        if (installedVersionName.isNullOrBlank()) return GitHubInstallState.NotInstalled
        val installed = SelfUpdateVersion.parse(installedVersionName)
            ?: return GitHubInstallState.Unknown
        val release = SelfUpdateVersion.parse(releaseTag) ?: return GitHubInstallState.Unknown
        return if (SelfUpdateVersion.compare(release, installed) > 0) {
            GitHubInstallState.UpdateAvailable
        } else {
            GitHubInstallState.Current
        }
    }
}
