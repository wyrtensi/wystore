package dev.wystore.background

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import dev.wystore.R
import dev.wystore.data.DownloadProgress
import dev.wystore.settings.AppSettings
import dev.wystore.settings.SettingsRepository
import dev.wystore.updates.model.QueueItemSnapshot
import java.util.Calendar
import java.util.Locale

/**
 * Every notification Wy Store posts.
 *
 * The contract is one notification per category, never one per app: a bulk update of twenty apps
 * produces a single "ready" entry listing them, and repeating a background check that finds the
 * same updates re-posts that entry silently instead of alerting again. [NotificationPolicy] holds
 * the rules; this class only renders them.
 */
class NotificationCoordinator(
    private val context: Context,
    private val limiter: ProgressNotificationLimiter = ProgressNotificationLimiter(),
    private val settingsRepository: SettingsRepository = SettingsRepository(context),
    private val state: NotificationState = NotificationState(context)
) {
    private val appContext = context.applicationContext
    private val notificationManager = appContext.getSystemService(NotificationManager::class.java)

    /**
     * The per-category switches in Settings are read here. They used to be persisted and then never
     * consulted, so turning a category off changed nothing.
     */
    private fun settings(): AppSettings = settingsRepository.currentSettings()

    private fun quietNow(settings: AppSettings, hourOfDay: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) =
        NotificationPolicy.isQuietHour(
            hourOfDay = hourOfDay,
            enabled = settings.quietHoursEnabled,
            startHour = settings.quietHoursStart,
            endHour = settings.quietHoursEnd
        )

    init {
        createChannels()
    }

    fun showTransfer(
        item: QueueItemSnapshot,
        downloadedBytes: Long = item.downloadedBytes,
        totalBytes: Long = item.totalBytes,
        speed: Long = 0,
        eta: Long? = null,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        val progress = DownloadProgress(
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            artifactIndex = 1,
            artifactCount = 1,
            bytesPerSecond = speed,
            etaSeconds = eta
        )
        if (!limiter.shouldPublish(progress, nowMillis, terminal = false)) {
            return
        }

        val percent = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
        val contentText = if (totalBytes > 0) {
            val mbDownloaded = downloadedBytes / 1_048_576.0
            val mbTotal = totalBytes / 1_048_576.0
            appContext.getString(R.string.notif_downloading_progress, mbDownloaded, mbTotal, percent)
        } else {
            appContext.getString(R.string.notif_downloading_indeterminate)
        }

        val openIntent = NotificationIntentFactory.createReadyPackagePendingIntent(
            context = appContext,
            packageName = item.packageName,
            queueId = item.id
        )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_TRANSFERS)
            .setSmallIcon(R.drawable.ic_stat_wystore)
            .setContentTitle(appContext.getString(R.string.notif_downloading_title, item.label))
            .setContentText(contentText)
            .setProgress(100, percent, totalBytes <= 0)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openIntent)
            .setOnlyAlertOnce(true)
            .build()

        safeNotify(NOTIFICATION_ID_TRANSFER, notification)
    }

    /**
     * The single "ready to install" entry.
     *
     * Passing an empty list removes it, which is how installing the last pending update clears the
     * shade; there is no per-package notification to leave behind any more.
     */
    fun publishReady(items: List<QueueItemSnapshot>) {
        val settings = settings()
        val digest = NotificationPolicy.digestOf(items.map { "${it.packageName}@${it.versionName}" })
        val decision = NotificationPolicy.decide(
            categoryEnabled = settings.readyNotificationsEnabled,
            digest = digest,
            lastPublishedDigest = state.lastDigest(NotificationState.CATEGORY_READY),
            quietHours = quietNow(settings)
        )
        if (!decision.post) {
            cancelReady()
            state.clear(NotificationState.CATEGORY_READY)
            return
        }

        val builder = NotificationCompat.Builder(appContext, CHANNEL_READY)
            .setSmallIcon(R.drawable.ic_stat_wystore)
            .setContentIntent(NotificationIntentFactory.createReadySummaryPendingIntent(appContext))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setSilent(!decision.alert)

        if (items.size == 1) {
            val single = items.first()
            builder
                .setContentTitle(appContext.getString(R.string.notif_ready_title))
                .setContentText(
                    appContext.getString(R.string.notif_ready_text, single.label, single.versionName)
                )
        } else {
            val style = NotificationCompat.InboxStyle()
                .setBigContentTitle(appContext.getString(R.string.notif_ready_summary_title))
            items.take(NotificationPolicy.MAX_LISTED_ITEMS).forEach { style.addLine("${it.label} ${it.versionName}") }
            if (items.size > NotificationPolicy.MAX_LISTED_ITEMS) {
                style.setSummaryText(
                    appContext.getString(
                        R.string.notif_ready_summary_more,
                        items.size - NotificationPolicy.MAX_LISTED_ITEMS
                    )
                )
            }
            builder
                .setContentTitle(appContext.getString(R.string.notif_ready_summary_title))
                .setContentText(appContext.getString(R.string.notif_ready_summary_count, items.size))
                .setStyle(style)
        }

        safeNotify(NOTIFICATION_ID_READY_SUMMARY, builder.build())
        state.recordDigest(NotificationState.CATEGORY_READY, digest)
    }

    /**
     * The single "these failed" entry, rendered from whatever is currently stuck.
     *
     * The error switch in Settings used to control a method nothing called; a failed background
     * download was silent and the user only found out by opening the app.
     */
    fun publishErrors(items: List<QueueItemSnapshot>) {
        val settings = settings()
        val digest = NotificationPolicy.digestOf(items.map { "${it.packageName}@${it.errorCode?.name.orEmpty()}" })
        val decision = NotificationPolicy.decide(
            categoryEnabled = settings.errorNotificationsEnabled,
            digest = digest,
            lastPublishedDigest = state.lastDigest(NotificationState.CATEGORY_ERRORS),
            quietHours = quietNow(settings)
        )
        if (!decision.post) {
            notificationManager.cancel(NOTIFICATION_ID_ERRORS)
            state.clear(NotificationState.CATEGORY_ERRORS)
            return
        }

        val text = if (items.size == 1) {
            appContext.getString(R.string.notif_errors_text_single, items.first().label)
        } else {
            appContext.getString(R.string.notif_errors_text, items.size)
        }
        val style = NotificationCompat.InboxStyle()
            .setBigContentTitle(appContext.getString(R.string.notif_errors_title))
        items.take(NotificationPolicy.MAX_LISTED_ITEMS).forEach { style.addLine(it.label) }

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ERRORS)
            .setSmallIcon(R.drawable.ic_stat_wystore)
            .setContentTitle(appContext.getString(R.string.notif_errors_title))
            .setContentText(text)
            .setStyle(style)
            .setContentIntent(NotificationIntentFactory.createReadySummaryPendingIntent(appContext))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setSilent(!decision.alert)
            .build()

        safeNotify(NOTIFICATION_ID_ERRORS, notification)
        state.recordDigest(NotificationState.CATEGORY_ERRORS, digest)
    }

    /**
     * The outcome of a finished check. Silent by design: it belongs on a low-importance channel and
     * a background check that found nothing does not post at all.
     */
    fun showCheckSummary(manual: Boolean, updatesFound: Int, problems: Int) {
        val settings = settings()
        if (!NotificationPolicy.shouldReportCheck(
                categoryEnabled = settings.checkSummaryNotificationsEnabled,
                manual = manual,
                updatesFound = updatesFound,
                problems = problems
            )
        ) {
            return
        }

        val text = when {
            problems > 0 -> appContext.getString(R.string.notif_check_summary_problems, updatesFound, problems)
            updatesFound > 0 -> appContext.getString(R.string.notif_check_summary_found, updatesFound)
            else -> appContext.getString(R.string.notif_check_summary_uptodate)
        }

        val notification = NotificationCompat.Builder(appContext, CHANNEL_CHECKS)
            .setSmallIcon(R.drawable.ic_stat_wystore)
            .setContentTitle(appContext.getString(R.string.notif_check_summary_title))
            .setContentText(text)
            .setContentIntent(NotificationIntentFactory.createReadySummaryPendingIntent(appContext))
            .setAutoCancel(true)
            .setSilent(true)
            .build()

        safeNotify(NOTIFICATION_ID_CHECK_SUMMARY, notification)
    }

    fun cancelReady() {
        notificationManager.cancel(NOTIFICATION_ID_READY_SUMMARY)
    }

    fun cancelErrors() {
        notificationManager.cancel(NOTIFICATION_ID_ERRORS)
        state.clear(NotificationState.CATEGORY_ERRORS)
    }

    fun cancelTransfer() {
        notificationManager.cancel(NOTIFICATION_ID_TRANSFER)
    }

    private fun speedText(bytesPerSecond: Long): String = if (bytesPerSecond < 1024 * 1024) {
        appContext.getString(R.string.notif_speed_kb, bytesPerSecond / 1024)
    } else {
        appContext.getString(
            R.string.notif_speed_mb,
            String.format(Locale.US, "%.1f", bytesPerSecond / 1024.0 / 1024.0)
        )
    }

    private fun etaSuffix(etaSeconds: Long?): String = etaSeconds?.let {
        " · " + if (it < 60) {
            appContext.getString(R.string.notif_eta_seconds, it)
        } else {
            appContext.getString(R.string.notif_eta_minutes, it / 60)
        }
    }.orEmpty()

    private fun safeNotify(id: Int, notification: android.app.Notification) {
        try {
            notificationManager.notify(id, notification)
        } catch (e: SecurityException) {
            // Permission denied on Android 13+; leave Room state unchanged
            android.util.Log.w("NotificationCoordinator", "Notification permission denied", e)
        }
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannelGroup(
                NotificationChannelGroup(GROUP_UPDATES, appContext.getString(R.string.channel_group_updates))
            )
            // Importance is fixed when a channel is first created and the system ignores later
            // changes, so the rebalanced channels carry new ids and the originals are removed.
            LEGACY_CHANNELS.forEach { runCatching { notificationManager.deleteNotificationChannel(it) } }
            val channels = listOf(
                channel(
                    CHANNEL_CHECKS,
                    R.string.channel_checks,
                    R.string.channel_checks_description,
                    NotificationManager.IMPORTANCE_MIN
                ),
                channel(
                    CHANNEL_TRANSFERS,
                    R.string.channel_transfers,
                    R.string.channel_transfers_description,
                    NotificationManager.IMPORTANCE_LOW
                ),
                channel(
                    CHANNEL_READY,
                    R.string.channel_ready,
                    R.string.channel_ready_description,
                    NotificationManager.IMPORTANCE_DEFAULT
                ),
                // Errors are the one thing worth interrupting for, but not with a full-screen
                // heads-up: DEFAULT rather than HIGH.
                channel(
                    CHANNEL_ERRORS,
                    R.string.channel_errors,
                    R.string.channel_errors_description,
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
            notificationManager.createNotificationChannels(channels)
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    private fun channel(id: String, nameRes: Int, descriptionRes: Int, importance: Int) =
        NotificationChannel(id, appContext.getString(nameRes), importance).apply {
            description = appContext.getString(descriptionRes)
            group = GROUP_UPDATES
            setShowBadge(id == CHANNEL_READY || id == CHANNEL_ERRORS)
        }

    companion object {
        const val CHANNEL_CHECKS = "wy_store_update_checks_v2"
        const val CHANNEL_TRANSFERS = "wy_store_active_transfers_v2"
        const val CHANNEL_READY = "wy_store_ready_updates_v2"
        const val CHANNEL_ERRORS = "wy_store_update_errors_v2"

        private val LEGACY_CHANNELS = listOf(
            "wy_store_update_checks",
            "wy_store_active_transfers",
            "wy_store_ready_updates",
            "wy_store_update_errors"
        )
        const val GROUP_UPDATES = "wy_store_group_updates"

        const val NOTIFICATION_ID_TRANSFER = 7001
        const val NOTIFICATION_ID_READY_SUMMARY = 7002
        const val NOTIFICATION_ID_ERRORS = 7003
        const val NOTIFICATION_ID_CHECK_SUMMARY = 7004
    }
}
