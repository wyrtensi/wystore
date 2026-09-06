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
    fun periodicCheckNeverEnqueuesDownloadWhenRootBackgroundDownloadsDisabled() {
        val shouldDownload = UpdateCheckPolicy.shouldEnqueueDownload(
            rootBackgroundDownloadsEnabled = false,
            isRootAvailable = true
        )
        assertFalse(shouldDownload)

        val shouldDownloadNoRoot = UpdateCheckPolicy.shouldEnqueueDownload(
            rootBackgroundDownloadsEnabled = false,
            isRootAvailable = false
        )
        assertFalse(shouldDownloadNoRoot)
    }

    @Test
    fun explicitRootBackgroundDownloadEnqueuesOnlyWhenRootAvailable() {
        val withRoot = UpdateCheckPolicy.shouldEnqueueDownload(
            rootBackgroundDownloadsEnabled = true,
            isRootAvailable = true
        )
        assertTrue(withRoot)

        val withoutRoot = UpdateCheckPolicy.shouldEnqueueDownload(
            rootBackgroundDownloadsEnabled = true,
            isRootAvailable = false
        )
        assertFalse(withoutRoot)
    }

    @Test
    fun manualPackageChecksRunEvenWhenAppAutoCheckFlagIsFalse() {
        // App has autoCheck = false
        val eligibleManualTargeted = UpdateCheckPolicy.evaluateAppEligibility(
            isManualCheck = true,
            targetPackageName = "dev.wystore.manualapp",
            appPackageName = "dev.wystore.manualapp",
            autoCheckEnabledForApp = false
        )
        assertTrue(eligibleManualTargeted)

        val eligibleManualAll = UpdateCheckPolicy.evaluateAppEligibility(
            isManualCheck = true,
            targetPackageName = null,
            appPackageName = "dev.wystore.manualapp",
            autoCheckEnabledForApp = false
        )
        assertTrue(eligibleManualAll)

        // But periodic check (not manual) must respect autoCheck = false
        val eligiblePeriodic = UpdateCheckPolicy.evaluateAppEligibility(
            isManualCheck = false,
            targetPackageName = null,
            appPackageName = "dev.wystore.manualapp",
            autoCheckEnabledForApp = false
        )
        assertFalse(eligiblePeriodic)
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

    @Test
    fun retryableFailuresYieldRetryAndPermanentFailuresDoNot() {
        val timeoutError = SocketTimeoutException("Connect timed out")
        assertTrue(UpdateCheckPolicy.shouldRetryWorker(timeoutError))

        val networkError = IOException("Network unreachable")
        assertTrue(UpdateCheckPolicy.shouldRetryWorker(networkError))

        val permanentNotFound = IllegalStateException("HTTP 404: Not Found")
        assertFalse(UpdateCheckPolicy.shouldRetryWorker(permanentNotFound))
    }
}
