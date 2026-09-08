package dev.wystore.background

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When an update may be fetched with nobody watching.
 *
 * Two independent paths lead here. The root path downloads so that the update can be installed
 * silently afterwards, and is worth nothing without root. Auto-download is the ordinary one: the
 * file is fetched as soon as a check finds it, and Android still asks before installing it. The
 * cases below keep the second from quietly disabling the first.
 */
class UnattendedUpdatePolicyTest {

    @Test
    fun rootAndTheSettingTogetherAllowAnUnattendedDownload() {
        assertTrue(
            UpdateCheckPolicy.shouldEnqueueDownload(
                autoDownloadEnabled = false,
                rootBackgroundDownloadsEnabled = true,
                isRootAvailable = true
            )
        )
    }

    @Test
    fun theRootPathIsWorthNothingWithoutRoot() {
        // It exists to make a silent install possible; on a phone that cannot install silently
        // there is nothing for it to prepare.
        assertFalse(
            UpdateCheckPolicy.shouldEnqueueDownload(
                autoDownloadEnabled = false,
                rootBackgroundDownloadsEnabled = true,
                isRootAvailable = false
            )
        )
    }

    @Test
    fun rootAloneIsNotConsent() {
        // A rooted device is not an invitation to spend its data unprompted.
        assertFalse(
            UpdateCheckPolicy.shouldEnqueueDownload(
                autoDownloadEnabled = false,
                rootBackgroundDownloadsEnabled = false,
                isRootAvailable = true
            )
        )
    }

    @Test
    fun neitherMeansNothing() {
        assertFalse(
            UpdateCheckPolicy.shouldEnqueueDownload(
                autoDownloadEnabled = false,
                rootBackgroundDownloadsEnabled = false,
                isRootAvailable = false
            )
        )
    }

    @Test
    fun autoDownloadNeedsNeitherRootNorTheRootSetting() {
        assertTrue(
            UpdateCheckPolicy.shouldEnqueueDownload(
                autoDownloadEnabled = true,
                rootBackgroundDownloadsEnabled = false,
                isRootAvailable = false
            )
        )
    }

    /**
     * Turning auto-download off must not take the root path down with it: someone who set up
     * silent root updates never asked for that.
     */
    @Test
    fun turningAutoDownloadOffLeavesTheRootPathAlone() {
        assertTrue(
            UpdateCheckPolicy.shouldEnqueueDownload(
                autoDownloadEnabled = false,
                rootBackgroundDownloadsEnabled = true,
                isRootAvailable = true
            )
        )
    }
}
