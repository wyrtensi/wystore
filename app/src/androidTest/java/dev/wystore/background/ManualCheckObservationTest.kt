package dev.wystore.background

import android.content.Context
import androidx.lifecycle.Observer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dev.wystore.data.StoreSettings
import dev.wystore.updates.UpdateScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The progress card for a manual check is driven by observing the work by its unique name.
 *
 * It stayed on "queued" forever, and because the check button is disabled while a check is active,
 * the button died with it until the app was restarted. These assertions pin the two things that
 * have to hold for that observation to work at all: the name the work is enqueued under is the name
 * the UI observes, and observing it actually delivers.
 */
@RunWith(AndroidJUnit4::class)
class ManualCheckObservationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun theUiObservesTheNameTheSchedulerEnqueuesUnder() {
        assertEquals(UpdateWorkScheduler.MANUAL_CHECK_WORK, UpdateScheduler.MANUAL_CHECK_WORK_NAME)
    }

    @Test
    fun observingThatNameDeliversTheEnqueuedWork() {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(UpdateWorkScheduler.MANUAL_CHECK_WORK)

        UpdateWorkScheduler.checkPackageNow(context, StoreSettings(wifiOnly = false, allowMobileData = true), null)

        val infos = workManager.getWorkInfosForUniqueWork(UpdateWorkScheduler.MANUAL_CHECK_WORK).get()
        assertTrue(
            "the scheduler must enqueue under the observed name, got $infos",
            infos.isNotEmpty()
        )
    }

    @Test
    fun theLiveDataEmitsToAForeverObserver() {
        val workManager = WorkManager.getInstance(context)
        UpdateWorkScheduler.checkPackageNow(context, StoreSettings(wifiOnly = false, allowMobileData = true), null)

        // Held in a local, exactly as the ViewModel must hold it: the factory builds a new LiveData
        // on every call, so observing an inline result leaves nothing keeping it alive.
        val liveData = workManager.getWorkInfosForUniqueWorkLiveData(UpdateWorkScheduler.MANUAL_CHECK_WORK)
        val delivered = CountDownLatch(1)
        var seen: List<WorkInfo>? = null
        val observer = Observer<List<WorkInfo>> { infos ->
            seen = infos
            if (infos.isNotEmpty()) delivered.countDown()
        }

        InstrumentationRegistry.getInstrumentation().runOnMainSync { liveData.observeForever(observer) }
        try {
            assertTrue(
                "observing the unique work delivered nothing within 10s; last value: $seen",
                delivered.await(10, TimeUnit.SECONDS)
            )
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { liveData.removeObserver(observer) }
        }
    }
}
