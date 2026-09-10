package dev.wystore.data

import org.junit.Assert.assertEquals
import org.junit.Test

class GitHubInstallStatePolicyTest {

    private fun state(installed: String?, tag: String?) =
        GitHubInstallStatePolicy.stateFor(installed, tag)

    @Test
    fun anAppThatIsNotThereIsOfferedForInstall() {
        assertEquals(GitHubInstallState.NotInstalled, state(null, "v1.2.3"))
        assertEquals(GitHubInstallState.NotInstalled, state("", "v1.2.3"))
    }

    /** The reported bug: the same version was still offered as an update. */
    @Test
    fun theSameVersionIsCurrentWhateverTheTagLooksLike() {
        assertEquals(GitHubInstallState.Current, state("1.2.3", "v1.2.3"))
        assertEquals(GitHubInstallState.Current, state("1.2.3", "1.2.3"))
        assertEquals(GitHubInstallState.Current, state("1.2", "v1.2.0"))
        assertEquals(GitHubInstallState.Current, state("1.2.3", "v1.2.3-rc1"))
    }

    @Test
    fun aNewerReleaseIsAnUpdate() {
        assertEquals(GitHubInstallState.UpdateAvailable, state("1.2.3", "v1.2.4"))
        assertEquals(GitHubInstallState.UpdateAvailable, state("1.9", "v1.10"))
        assertEquals(GitHubInstallState.UpdateAvailable, state("0.9.9", "v1.0.0"))
    }

    @Test
    fun anOlderReleaseIsNotAnUpdate() {
        // Browsing an older release of an app that is already ahead of it. Not "current" either:
        // nothing here can install over what is on the phone.
        assertEquals(GitHubInstallState.InstalledNewer, state("2.0.0", "v1.9.9"))
    }

    @Test
    fun versionsNobodyCanCompareSaySo() {
        // A date-stamped or codename tag: offering "Update" would be a guess, and so would
        // claiming the app is current.
        assertEquals(GitHubInstallState.Unknown, state("1.2.3", "nightly"))
        assertEquals(GitHubInstallState.Unknown, state("stable-channel", "v1.2.3"))
    }

    /**
     * The tag ByeByeDPI actually publishes. The background check kept queueing this release for a
     * phone already running it, downloading it and failing verification on "not newer than
     * installed" - which the user was shown as a signature problem.
     */
    @Test
    fun aReleaseTaggedWithADotAfterTheVIsStillTheInstalledVersion() {
        assertEquals(
            GitHubInstallState.Current,
            GitHubInstallStatePolicy.stateFor(installedVersionName = "1.7.8", releaseTag = "v.1.7.8")
        )
        assertEquals(
            GitHubInstallState.UpdateAvailable,
            GitHubInstallStatePolicy.stateFor(installedVersionName = "1.7.8", releaseTag = "v.1.7.9")
        )
    }

    /**
     * A version string neither side can read is not an invitation to guess. The background check
     * queues on UpdateAvailable alone, because queueing on Unknown is how the same release came
     * round again on every check.
     */
    @Test
    fun anUnreadableVersionIsNotAnUpdate() {
        assertEquals(
            GitHubInstallState.Unknown,
            GitHubInstallStatePolicy.stateFor(installedVersionName = "nightly", releaseTag = "v2.0")
        )
        assertEquals(
            GitHubInstallState.Unknown,
            GitHubInstallStatePolicy.stateFor(installedVersionName = "1.0", releaseTag = "rolling")
        )
    }
}
