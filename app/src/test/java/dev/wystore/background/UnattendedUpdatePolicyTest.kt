package dev.wystore.background

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When an update may be downloaded with nobody watching.
 *
 * The rule below was written and tested from the start, but nothing in the app ever called it: the
 * check worker only queued a row, and the download still waited behind a button press. So both
 * root switches in Settings did nothing on their own, and "background updates" meant "background
 * checks". These assertions pin the rule now that the worker actually uses it.
 */
class UnattendedUpdatePolicyTest {

    @Test
    fun rootAndTheSettingTogetherAllowAnUnattendedDownload() {
        assertTrue(
            UpdateCheckPolicy.shouldEnqueueDownload(
                rootBackgroundDownloadsEnabled = true,
                isRootAvailable = true
            )
        )
    }

    @Test
    fun withoutRootNothingDownloadsUnattended() {
        // Without root the install still needs the Android confirmation dialog, so downloading
        // ahead of time would only fill storage with files that cannot be installed unattended.
        assertFalse(
            UpdateCheckPolicy.shouldEnqueueDownload(
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
                rootBackgroundDownloadsEnabled = false,
                isRootAvailable = true
            )
        )
    }

    @Test
    fun neitherMeansNothing() {
        assertFalse(
            UpdateCheckPolicy.shouldEnqueueDownload(
                rootBackgroundDownloadsEnabled = false,
                isRootAvailable = false
            )
        )
    }
}
