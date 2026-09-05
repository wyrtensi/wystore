package dev.wystore.permissions

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PermissionRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: PermissionRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repository = PermissionRepository(context)
    }

    @Test
    fun generatesValidIntents() {
        val appDetailsIntent = repository.applicationDetailsIntent()
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appDetailsIntent.action)
        assertEquals("package:${context.packageName}", appDetailsIntent.dataString)

        val unknownSourcesIntent = repository.unknownSourcesIntent()
        assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, unknownSourcesIntent.action)
        assertEquals("package:${context.packageName}", unknownSourcesIntent.dataString)

        val notifIntent = repository.notificationSettingsIntent()
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, notifIntent.action)

        val channelIntent = repository.channelSettingsIntent("test_channel")
        assertEquals(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS, channelIntent.action)

        val batteryIntent = repository.batteryOptimizationSettingsIntent()
        assertNotNull(batteryIntent.action)
    }

    @Test
    fun snapshotReturnsCleanly() {
        val snapshot = repository.snapshot()
        assertNotNull(snapshot)
    }
}
