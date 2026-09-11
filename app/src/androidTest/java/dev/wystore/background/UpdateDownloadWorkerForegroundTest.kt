package dev.wystore.background

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Data
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Asks the worker for its notification the way WorkManager does before starting an expedited
 * request below API 31.
 *
 * The worker inherited an implementation that throws, and because nothing ever called it in a
 * test, every user-started transfer on Android 9, 10 and 11 failed before the worker ran: the
 * queue row stayed where it was and no failure was recorded, because recording happens inside
 * doWork(). This is the call that was missing, made directly.
 *
 * It runs on any API level on purpose. WorkManager only makes this call below 31, but the
 * requirement belongs to the worker, not to the device it happens to be tested on.
 */
@RunWith(AndroidJUnit4::class)
class UpdateDownloadWorkerForegroundTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun theWorkerAnswersWithANotificationBeforeItStarts() {
        val worker = TestListenableWorkerBuilder<UpdateDownloadWorker>(context)
            .setInputData(
                Data.Builder()
                    .putString(UpdateDownloadWorker.KEY_QUEUE_ID, "row-that-does-not-exist")
                    .build()
            )
            .build()

        val info = runBlocking { worker.getForegroundInfo() }

        assertEquals(NotificationCoordinator.NOTIFICATION_ID_TRANSFER, info.notificationId)
    }

    /** And with no queue id at all, which is how a malformed request would arrive. */
    @Test
    fun anUnreadableRequestStillProducesANotificationRatherThanAnException() {
        val worker = TestListenableWorkerBuilder<UpdateDownloadWorker>(context).build()

        val info = runBlocking { worker.getForegroundInfo() }

        assertEquals(NotificationCoordinator.NOTIFICATION_ID_TRANSFER, info.notificationId)
    }
}
