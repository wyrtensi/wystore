package dev.wystore.background

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.wystore.R
import dev.wystore.data.ManagedSource
import dev.wystore.data.local.UpdateQueueEntity
import dev.wystore.data.local.toSnapshot
import dev.wystore.updates.model.QueueState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransferNotificationRankingTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val fixtureId = 7988

    @After fun cleanUp() {
        manager.cancel(NotificationCoordinator.NOTIFICATION_ID_TRANSFER)
        manager.cancel(NotificationCoordinator.NOTIFICATION_ID_READY_SUMMARY)
        manager.cancel(NotificationCoordinator.NOTIFICATION_ID_ERRORS)
        manager.cancel(NotificationCoordinator.NOTIFICATION_ID_CHECK_SUMMARY)
        manager.cancel(fixtureId)
    }

    @Test fun foregroundBootstrapDoesNotRefreshOrAlert() {
        val notification = DownloadForegroundInfoFactory.createForegroundInfo(context, "Example").notification
        assertEquals("foreground timestamp should be inherited on updates", 0L, notification.`when`)
        assertTrue("foreground updates alert only once", notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertTrue("foreground notification remains ongoing", notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(false, notification.extras.getBoolean(Notification.EXTRA_SHOW_WHEN))
    }

    @Test fun progressUpdatesKeepTheSystemRankingTime() {
        val coordinator = NotificationCoordinator(context)
        val item = UpdateQueueEntity(id = "ranking-test", packageName = "example.ranking", label = "Example",
            versionName = "1", versionCode = 1L, source = ManagedSource.RUSTORE.name,
            state = QueueState.DOWNLOADING.name).toSnapshot()
        coordinator.showTransfer(item, 1, 100, nowMillis = 10_000)
        Thread.sleep(500)
        val before = rankingTime(NotificationCoordinator.NOTIFICATION_ID_TRANSFER)
        val adjacent = NotificationCompat.Builder(context, NotificationCoordinator.CHANNEL_TRANSFERS)
            .setSmallIcon(R.drawable.ic_stat_wystore).setContentTitle("Adjacent ongoing test")
            .setOngoing(true).setSilent(true).build()
        manager.notify(fixtureId, adjacent)
        val observed = listOf(25L, 50L, 75L).mapIndexed { index, percent ->
            Thread.sleep(500)
            coordinator.showTransfer(item, percent, 100, nowMillis = 20_000L + index * 10_000L)
            Thread.sleep(300)
            rankingTime(NotificationCoordinator.NOTIFICATION_ID_TRANSFER)
        }
        val fixture = rankingTime(fixtureId)
        Log.i("TransferRankingTest", "before=$before after=$observed adjacent=$fixture")
        observed.forEach { assertEquals("progress updates must preserve Android ranking time", before, it) }
        assertTrue("test fixture should be newer", fixture > before)
        val active = manager.activeNotifications.single { it.id == NotificationCoordinator.NOTIFICATION_ID_TRANSFER }.notification
        assertEquals(0L, active.`when`)
        assertEquals("latest progress is visible", 75, active.extras.getInt(Notification.EXTRA_PROGRESS))
        assertTrue(active.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertEquals(false, active.extras.getBoolean(Notification.EXTRA_SHOW_WHEN))
    }

    @Test fun repeatedSummariesKeepRankingButChangedDigestIsFresh() {
        val repository = dev.wystore.data.StoreRepository(context)
        val previousSettings = repository.settings()
        val state = NotificationState(context)
        val categories = listOf(NotificationState.CATEGORY_READY, NotificationState.CATEGORY_ERRORS)
        val previous = categories.associateWith(state::lastDigest)
        try {
            repository.saveSettings(previousSettings.copy(readyNotificationsEnabled = true,
                errorNotificationsEnabled = true, checkSummaryNotificationsEnabled = true))
            categories.forEach(state::clear)
            val coordinator = NotificationCoordinator(context)
            val item = UpdateQueueEntity(id = "summary-test", packageName = "example.summary", label = "Example",
                versionName = "1", versionCode = 1L, source = ManagedSource.RUSTORE.name,
                state = QueueState.READY_TO_INSTALL.name).toSnapshot()
            for ((id, publish) in listOf(
                NotificationCoordinator.NOTIFICATION_ID_READY_SUMMARY to { version: String ->
                    coordinator.publishReady(listOf(item.copy(versionName = version)))
                },
                NotificationCoordinator.NOTIFICATION_ID_ERRORS to { version: String ->
                    coordinator.publishErrors(listOf(item.copy(packageName = "example.$version")))
                }
            )) {
                publish("one")
                Thread.sleep(200)
                val first = rankingTime(id)
                publish("one")
                Thread.sleep(200)
                assertEquals("unchanged summary ranking $id", first, rankingTime(id))
                publish("two")
                Thread.sleep(200)
                assertTrue("changed summary should be fresh $id", rankingTime(id) > first)
                manager.cancel(id)
                publish("two")
                Thread.sleep(200)
                assertTrue("dismissed summary can be restored $id", manager.activeNotifications.any { it.id == id })
            }
            coordinator.showCheckSummary(manual = true, updatesFound = 2, problems = 0)
            Thread.sleep(200)
            val check = rankingTime(NotificationCoordinator.NOTIFICATION_ID_CHECK_SUMMARY)
            coordinator.showCheckSummary(manual = true, updatesFound = 2, problems = 0)
            Thread.sleep(200)
            assertEquals("repeated check ranking", check, rankingTime(NotificationCoordinator.NOTIFICATION_ID_CHECK_SUMMARY))
        } finally {
            repository.saveSettings(previousSettings)
            previous.forEach { (category, digest) ->
                if (digest == null) state.clear(category) else state.recordDigest(category, digest)
            }
        }
    }

    private fun rankingTime(id: Int): Long {
        val pfd: ParcelFileDescriptor = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("dumpsys notification --noredact")
        val dump = pfd.use { ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText() }
        val record = dump.split("NotificationRecord(").firstOrNull {
            it.contains("pkg=${context.packageName} ") && Regex("\\bid=$id\\b").containsMatchIn(it)
        } ?: error("notification $id missing from dumpsys")
        return Regex("(?:mRankingTimeMs|rankingTimeMs)=(\\d+)").find(record)?.groupValues?.get(1)?.toLong()
            ?: error("ranking time missing: ${record.take(3000)}")
    }
}
