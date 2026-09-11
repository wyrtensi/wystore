package dev.wystore.background

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The contract that was broken for every install on Android 9, 10 and 11.
 *
 * Below API 31 WorkManager can only honour "expedited" by running the worker as a foreground
 * service, so it asks the worker for a notification before starting it. The inherited
 * implementation throws, and the request fails before [UpdateDownloadWorker.doWork] runs: no
 * download, no error, no log entry, the queue row left sitting at AVAILABLE. On API 31 and above
 * the platform runs an expedited job and never asks, which is why nothing showed on a newer phone.
 *
 * Reflection rather than a run: WorkManager's test builders call doWork() directly and never enter
 * the code path that asks for the notification, so a green worker test proves nothing here.
 */
class ExpeditedWorkContractTest {

    @Test
    fun theDownloadWorkerProvidesTheNotificationExpeditedWorkNeeds() {
        val declared = UpdateDownloadWorker::class.java.declaredMethods.map { it.name }

        assertTrue(
            "UpdateDownloadWorker is enqueued as expedited work and must override " +
                "getForegroundInfo(), or every transfer fails before it starts on API < 31",
            declared.contains("getForegroundInfo") || declared.contains("getForegroundInfoAsync")
        )
    }

    /**
     * The other half: a worker that provides no notification must not ask to be expedited. A
     * version check is a few kilobytes and has no business becoming a foreground service.
     */
    @Test
    fun theCheckWorkerAsksToBeExpeditedOnlyWhereNoNotificationIsRequired() {
        val declared = UpdateCheckWorker::class.java.declaredMethods.map { it.name }
        val providesForegroundInfo =
            declared.contains("getForegroundInfo") || declared.contains("getForegroundInfoAsync")

        assertEquals(false, providesForegroundInfo)
        assertEquals(false, UpdateCheckPolicy.checkMayBeExpedited(sdkInt = 28))
        assertEquals(false, UpdateCheckPolicy.checkMayBeExpedited(sdkInt = 29))
        assertEquals(false, UpdateCheckPolicy.checkMayBeExpedited(sdkInt = 30))
        assertEquals(true, UpdateCheckPolicy.checkMayBeExpedited(sdkInt = 31))
        assertEquals(true, UpdateCheckPolicy.checkMayBeExpedited(sdkInt = 36))
    }
}
