package dev.wystore.background

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import dev.wystore.data.GooglePlayUpdateMonitor
import dev.wystore.data.ManagedApp
import dev.wystore.data.StoreRepository
import dev.wystore.settings.AppSettings
import dev.wystore.settings.SettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GooglePlayPauseNotificationTest {
    private val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            super.getSharedPreferences("pause_notification_test_$name", mode)
    }
    private val manager get() = context.getSystemService(NotificationManager::class.java)
    private val settings = SettingsRepository(context)
    private lateinit var originalSettings: AppSettings
    private val app = ManagedApp("test.pause.notification", "Pause fixture", setOf("certificate"),
        googlePlayUpdateDetected = true)
    private val id = NotificationCoordinator.NOTIFICATION_ID_GOOGLE_PLAY_PAUSE

    @Before fun prepare() = runBlocking {
        originalSettings = settings.currentSettings()
        settings.update { it.copy(errorNotificationsEnabled = true, quietHoursEnabled = false,
            continueChecksAfterGooglePlayUpdate = false) }
        NotificationState(context).clear(NotificationState.CATEGORY_GOOGLE_PLAY_PAUSE)
        manager.cancel(id)
        awaitNotification(false)
        StoreRepository(context).removeManaged(app.packageName)
    }

    @After fun restore() = runBlocking {
        manager.cancel(id)
        awaitNotification(false)
        NotificationState(context).clear(NotificationState.CATEGORY_GOOGLE_PLAY_PAUSE)
        StoreRepository(context).removeManaged(app.packageName)
        settings.update { originalSettings }
    }

    @Test fun aDismissedPauseDoesNotReappearOnTheNextRefresh() {
        val coordinator = NotificationCoordinator(context)
        coordinator.publishGooglePlayPause(listOf(app))
        awaitNotification(true)
        manager.cancel(id)
        awaitNotification(false)
        coordinator.publishGooglePlayPause(listOf(app))
        assertStaysDismissed()
    }

    @Test fun disabledAppChecksDoNotGenerateAPauseNotification() = runBlocking {
        StoreRepository(context).saveManaged(app.copy(autoUpdate = false))
        GooglePlayUpdateMonitor(context).refresh(emptyList())
        assertStaysDismissed()
    }

    @Test fun aNewIncidentPublishesButRemovingItDoesNotReannounceTheOldOne() {
        val coordinator = NotificationCoordinator(context)
        coordinator.publishGooglePlayPause(listOf(app))
        awaitNotification(true)
        manager.cancel(id)
        awaitNotification(false)
        coordinator.publishGooglePlayPause(listOf(app, app.copy(packageName = "test.pause.second")))
        awaitNotification(true)
        manager.cancel(id)
        awaitNotification(false)
        coordinator.publishGooglePlayPause(listOf(app))
        assertStaysDismissed()
    }

    @Test fun removingAPausedAppUpdatesTheActiveNotification() {
        val coordinator = NotificationCoordinator(context)
        val second = app.copy(packageName = "test.pause.second", label = "Second fixture")
        coordinator.publishGooglePlayPause(listOf(app, second))
        awaitNotification(true)
        coordinator.publishGooglePlayPause(listOf(second))

        val deadline = android.os.SystemClock.uptimeMillis() + 2_000
        var text = ""
        do {
            text = manager.activeNotifications.firstOrNull { it.id == id }
                ?.notification?.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
            if (text.contains(second.label) && !text.contains(app.label)) return
            android.os.SystemClock.sleep(10)
        } while (android.os.SystemClock.uptimeMillis() < deadline)
        assertTrue("Notification should contain the remaining app: $text", text.contains(second.label))
        assertFalse("Notification should omit the removed app: $text", text.contains(app.label))
    }

    private fun awaitNotification(present: Boolean) {
        val deadline = android.os.SystemClock.uptimeMillis() + 2_000
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            if (manager.activeNotifications.any { it.id == id } == present) return
            android.os.SystemClock.sleep(10)
        }
        assertEquals(present, manager.activeNotifications.any { it.id == id })
    }

    private fun assertStaysDismissed() {
        // Notification cancellation is asynchronous in the system service. Observe the shade
        // after dismissal has completed, including a bounded dispatch window for a wrong repost.
        val deadline = android.os.SystemClock.uptimeMillis() + 500
        do {
            assertFalse(manager.activeNotifications.any { it.id == id })
            android.os.SystemClock.sleep(10)
        } while (android.os.SystemClock.uptimeMillis() < deadline)
    }
}
