package dev.wystore.background

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.wystore.data.DownloadProgress
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdateDownloadWorkerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun foregroundInfoUsesNotificationId7001AndDataSyncType() {
        val progress = DownloadProgress(
            downloadedBytes = 50_000_000L,
            totalBytes = 100_000_000L,
            artifactIndex = 1,
            artifactCount = 1
        )
        val info = DownloadForegroundInfoFactory.createForegroundInfo(
            context = context,
            label = "Test App",
            progress = progress
        )

        assertEquals(NotificationCoordinator.NOTIFICATION_ID_TRANSFER, info.notificationId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC, info.foregroundServiceType)
        }
    }
}
