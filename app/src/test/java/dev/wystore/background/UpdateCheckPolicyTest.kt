package dev.wystore.background

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import dev.wystore.updates.UpdateScheduler
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

class UpdateCheckPolicyTest {

    @Test
    fun nothingIsFetchedWhenBothAutoDownloadAndTheRootPathAreOff() {
        val shouldDownload = UpdateCheckPolicy.shouldEnqueueDownload(
            autoDownloadEnabled = false,
            rootBackgroundDownloadsEnabled = false,
            isRootAvailable = true
        )
        assertFalse(shouldDownload)

        val shouldDownloadNoRoot = UpdateCheckPolicy.shouldEnqueueDownload(
            autoDownloadEnabled = false,
            rootBackgroundDownloadsEnabled = false,
            isRootAvailable = false
        )
        assertFalse(shouldDownloadNoRoot)
    }

    @Test
    fun explicitRootBackgroundDownloadEnqueuesOnlyWhenRootAvailable() {
        val withRoot = UpdateCheckPolicy.shouldEnqueueDownload(
            autoDownloadEnabled = false,
            rootBackgroundDownloadsEnabled = true,
            isRootAvailable = true
        )
        assertTrue(withRoot)

        val withoutRoot = UpdateCheckPolicy.shouldEnqueueDownload(
            autoDownloadEnabled = false,
            rootBackgroundDownloadsEnabled = true,
            isRootAvailable = false
        )
        assertFalse(withoutRoot)
    }

    /**
     * The point of the setting: an update found on a phone without root is fetched too, so the
     * install offer arrives with the file already there instead of after a second wait.
     */
    @Test
    fun autoDownloadFetchesWithoutRoot() {
        val withoutRoot = UpdateCheckPolicy.shouldEnqueueDownload(
            autoDownloadEnabled = true,
            rootBackgroundDownloadsEnabled = false,
            isRootAvailable = false
        )
        assertTrue(withoutRoot)
    }

    /**
     * The check the user pressed has to run on the connection the phone has. Requiring an unmetered
     * one left the job with an unsatisfied CONNECTIVITY constraint on mobile data, and the Library
     * said "queued" until the phone found Wi-Fi - which on a phone that never does is forever.
     */
    @Test
    fun aManualCheckRunsOnWhateverConnectionThereIs() {
        assertFalse(
            UpdateCheckPolicy.checkRequiresUnmeteredNetwork(
                isManualCheck = true,
                wifiOnly = true,
                allowMobileData = false
            )
        )
    }

    @Test
    fun aPeriodicCheckStillKeepsTheWifiOnlySetting() {
        assertTrue(
            UpdateCheckPolicy.checkRequiresUnmeteredNetwork(
                isManualCheck = false,
                wifiOnly = true,
                allowMobileData = false
            )
        )
        assertFalse(
            UpdateCheckPolicy.checkRequiresUnmeteredNetwork(
                isManualCheck = false,
                wifiOnly = true,
                allowMobileData = true
            )
        )
        assertFalse(
            UpdateCheckPolicy.checkRequiresUnmeteredNetwork(
                isManualCheck = false,
                wifiOnly = false,
                allowMobileData = false
            )
        )
    }

    /** A check aimed at one package is a request for exactly that package. */
    @Test
    fun aCheckAimedAtOneAppRunsWhateverItsSwitchSays() {
        assertTrue(
            UpdateCheckPolicy.evaluateAppEligibility(
                isManualCheck = true,
                targetPackageName = "dev.wystore.manualapp",
                appPackageName = "dev.wystore.manualapp",
                autoCheckEnabledForApp = false
            )
        )
    }

    /**
     * "Auto-update off" used to mean "not on a schedule, but yes when someone presses check", so a
     * check over the whole library left a row in the queue for an app the user had deliberately
     * taken out of automatic updating. Nothing downloaded it; it simply sat there being offered.
     */
    @Test
    fun anExcludedAppIsLeftOutOfAWholeLibraryCheck() {
        for (manual in listOf(true, false)) {
            assertFalse(
                UpdateCheckPolicy.evaluateAppEligibility(
                    isManualCheck = manual,
                    targetPackageName = null,
                    appPackageName = "dev.wystore.manualapp",
                    autoCheckEnabledForApp = false
                )
            )
        }
    }

    /** ...unless the person has asked to see those updates anyway. Shown, never fetched. */
    @Test
    fun anExcludedAppIsCheckedWhenItsUpdatesAreAskedFor() {
        assertTrue(
            UpdateCheckPolicy.evaluateAppEligibility(
                isManualCheck = false,
                targetPackageName = null,
                appPackageName = "dev.wystore.manualapp",
                autoCheckEnabledForApp = false,
                showExcludedUpdates = true
            )
        )
        assertFalse(UpdateCheckPolicy.mayDownloadAfterCheck(autoUpdateEnabledForApp = false))
    }

    @Test
    fun anAppThatUpdatesItselfIsAlwaysChecked() {
        assertTrue(
            UpdateCheckPolicy.evaluateAppEligibility(
                isManualCheck = false,
                targetPackageName = null,
                appPackageName = "dev.wystore.manualapp",
                autoCheckEnabledForApp = true
            )
        )
    }

    /**
     * A manual check used to get a unique work name per target package. Nothing could observe that:
     * the ViewModel registers one observer for one fixed name, and it was observing a third name
     * that nothing enqueued, so the progress card never left "queued" and the check button — which
     * is disabled while a check is active — stayed dead until the app restarted.
     */
    @Test
    fun everyManualCheckSharesOneObservableName() {
        assertEquals("wystore:updates:manual", UpdateWorkScheduler.MANUAL_CHECK_WORK)
        assertEquals(UpdateWorkScheduler.MANUAL_CHECK_WORK, UpdateScheduler.MANUAL_CHECK_WORK_NAME)
        assertNotEquals(UpdateWorkScheduler.MANUAL_CHECK_WORK, UpdateWorkScheduler.PERIODIC_CHECK_WORK)
        assertEquals("wystore:updates:periodic", UpdateWorkScheduler.PERIODIC_CHECK_WORK)
    }

    /**
     * The library switch promises, in the app's own words, that a check "will not download or
     * install" the app's updates. A check over the whole library kept looking at excluded apps -
     * which is right, the update is worth showing - and then fetched every one of them anyway.
     */
    @Test
    fun anAppExcludedFromAutoUpdatesIsNeverFetchedByACheck() {
        assertFalse(UpdateCheckPolicy.mayDownloadAfterCheck(autoUpdateEnabledForApp = false))
        assertTrue(UpdateCheckPolicy.mayDownloadAfterCheck(autoUpdateEnabledForApp = true))
    }

    @Test
    fun retryableFailuresYieldRetryAndPermanentFailuresDoNot() {
        val timeoutError = SocketTimeoutException("Connect timed out")
        assertTrue(UpdateCheckPolicy.shouldRetryWorker(timeoutError))

        val networkError = IOException("Network unreachable")
        assertTrue(UpdateCheckPolicy.shouldRetryWorker(networkError))

        val permanentNotFound = IllegalStateException("HTTP 404: Not Found")
        assertFalse(UpdateCheckPolicy.shouldRetryWorker(permanentNotFound))
    }

    /**
     * One app the source cannot answer for is a problem to report, not a reason to run the whole
     * round again - and with an app the store simply does not carry, "again" never ends.
     */
    @Test
    fun aCheckIsRepeatedOnlyWhenEveryAttemptFailed() {
        assertFalse(UpdateCheckPolicy.shouldRetryCheck(attempted = 17, retryableFailures = 1))
        assertFalse(UpdateCheckPolicy.shouldRetryCheck(attempted = 0, retryableFailures = 0))
        assertFalse(UpdateCheckPolicy.shouldRetryCheck(attempted = 5, retryableFailures = 0))
        assertTrue(UpdateCheckPolicy.shouldRetryCheck(attempted = 5, retryableFailures = 5))
    }

    /**
     * The check button names what pressing it will do, and it must not promise an install that
     * cannot happen: with automatic downloads off a check finds updates and fetches nothing, so
     * there is never anything to hand to the installer.
     */
    @Test
    fun `the button promises installs only when a check can actually reach the installer`() {
        assertTrue(
            UpdateCheckPolicy.checkWillInstallWhatItFinds(
                autoDownloadEnabled = true,
                autoInstallEnabled = true
            )
        )
        assertFalse(
            UpdateCheckPolicy.checkWillInstallWhatItFinds(
                autoDownloadEnabled = false,
                autoInstallEnabled = true
            )
        )
        assertFalse(
            UpdateCheckPolicy.checkWillInstallWhatItFinds(
                autoDownloadEnabled = true,
                autoInstallEnabled = false
            )
        )
    }
}
