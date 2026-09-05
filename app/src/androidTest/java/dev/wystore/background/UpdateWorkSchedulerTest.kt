package dev.wystore.background

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
import dev.wystore.data.StoreSettings
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdateWorkSchedulerTest {

    private lateinit var context: Context
    private val settings = StoreSettings(
        updateIntervalHours = 6,
        allowMobileData = true,
        wifiOnly = false,
        requiresCharging = false
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun enqueuesPeriodicCheckSuccessfully() {
        UpdateWorkScheduler.schedulePeriodicCheck(context, settings)
        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(UpdateWorkScheduler.PERIODIC_CHECK_WORK)
            .get()
        assertNotNull(workInfos)
    }

    @Test
    fun enqueuesManualCheckSuccessfully() {
        UpdateWorkScheduler.checkPackageNow(context, settings, "dev.wystore.testapp")
        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(UpdateWorkScheduler.manualCheckWork("dev.wystore.testapp"))
            .get()
        assertNotNull(workInfos)
    }
}
