package dev.wystore.background

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun twoDifferentPackageChecksReceiveDifferentUniqueNames() {
        val name1 = UpdateWorkScheduler.manualCheckWork("app.first")
        val name2 = UpdateWorkScheduler.manualCheckWork("app.second")
        val nameAll = UpdateWorkScheduler.manualCheckWork(null)

        assertNotEquals(name1, name2)
        assertNotEquals(name1, nameAll)
        assertEquals("wystore:updates:manual:app.first", name1)
        assertEquals("wystore:updates:manual:app.second", name2)
        assertEquals("wystore:updates:manual:all", nameAll)
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
