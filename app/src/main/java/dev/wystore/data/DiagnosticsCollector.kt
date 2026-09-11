package dev.wystore.data

import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dev.wystore.BuildConfig
import dev.wystore.background.NotificationCoordinator
import dev.wystore.background.TransferDispatcher
import dev.wystore.background.UpdateWorkScheduler
import dev.wystore.permissions.PermissionRepository
import dev.wystore.root.RootInstaller
import dev.wystore.updates.QueueRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Gathers what a bug report needs from the device and from the store's own state.
 *
 * Root is checked here rather than remembered, because "is root available" is a question with a
 * different answer after the user grants or revokes it, and a report that says something stale is
 * worse than one that says nothing. It runs a shell command, so this is only ever called when
 * somebody asks for a report.
 *
 * Everything the system can be asked is asked here too, and every one of those questions is
 * wrapped: a report is collected because something is already wrong, so one unavailable service
 * must never be the reason the whole report fails to appear.
 */
class DiagnosticsCollector(context: Context) {

    private val appContext = context.applicationContext

    suspend fun collect(): Diagnostics = withContext(Dispatchers.IO) {
        val storeRepository = StoreRepository(appContext)
        val settings = storeRepository.settings()
        val permissions = PermissionRepository(appContext).snapshot()
        val metrics = appContext.resources.displayMetrics
        val configuration = appContext.resources.configuration
        val queue = runCatching { QueueRepository.getInstance(appContext).snapshotAll() }
            .getOrDefault(emptyList())

        Diagnostics(
            appVersionName = BuildConfig.VERSION_NAME,
            appVersionCode = BuildConfig.VERSION_CODE.toLong(),
            applicationId = appContext.packageName,
            targetSdk = appContext.applicationInfo.targetSdkVersion,
            installerOfSelf = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    appContext.packageManager
                        .getInstallSourceInfo(appContext.packageName).installingPackageName
                } else {
                    null
                }
            }.getOrNull(),
            androidRelease = Build.VERSION.RELEASE ?: "?",
            sdkInt = Build.VERSION.SDK_INT,
            securityPatch = Build.VERSION.SECURITY_PATCH.takeIf { !it.isNullOrBlank() },
            manufacturer = Build.MANUFACTURER ?: "?",
            model = Build.MODEL ?: "?",
            device = Build.DEVICE ?: "?",
            abis = Build.SUPPORTED_ABIS?.toList().orEmpty(),
            screenWidthPx = metrics.widthPixels,
            screenHeightPx = metrics.heightPixels,
            densityDpi = metrics.densityDpi,
            smallestWidthDp = configuration.smallestScreenWidthDp,
            fontScale = configuration.fontScale,
            locale = runCatching { configuration.locales[0].toString() }
                .getOrDefault(Locale.getDefault().toString()),
            rootAvailable = runCatching { RootInstaller().isAvailable() }.getOrNull(),
            rootSilentInstall = settings.rootSilentInstallEnabled,
            rootBackgroundDownloads = settings.rootBackgroundDownloadsEnabled,
            networkSummary = describeNetwork(),
            dataSaver = describeDataSaver(),
            transferMechanism = TransferDispatcher.determineMechanism().name,
            cacheFreeBytes = runCatching { appContext.cacheDir.usableSpace }.getOrDefault(-1L),
            notificationsGranted = permissions.notificationsGranted,
            notificationChannels = describeNotificationChannels(),
            canInstallUnknownApps = permissions.canInstallUnknownApps,
            batteryOptimizationsIgnored = permissions.batteryOptimizationsIgnored,
            powerSaveMode = runCatching {
                appContext.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
            }.getOrDefault(false),
            deviceIdleMode = runCatching {
                appContext.getSystemService(PowerManager::class.java)?.isDeviceIdleMode == true
            }.getOrDefault(false),
            standbyBucket = describeStandbyBucket(),
            managedApps = storeRepository.managedApps().size,
            githubRepositories = storeRepository.githubRepositories().size,
            queueRows = queue.map { item ->
                DiagnosticsQueueRow(
                    headline = "${item.packageName} ${item.versionName} ${item.state}" +
                        (item.errorCode?.let { " ${it.name}" } ?: "") +
                        (item.errorDetail?.let { ": $it" } ?: ""),
                    details = listOf(
                        "источник: ${item.source.name}, позиция ${item.position}, " +
                            "приоритет ${item.priority}",
                        "скачано: ${item.downloadedBytes / 1_048_576} / " +
                            "${item.totalBytes / 1_048_576} МБ",
                        "задача: ${describeWork(TransferDispatcher.downloadWorkName(item.id))}"
                    )
                )
            },
            backgroundWork = listOf(
                "проверка (по кнопке)" to describeWork(UpdateWorkScheduler.MANUAL_CHECK_WORK),
                "проверка (по расписанию)" to describeWork(UpdateWorkScheduler.PERIODIC_CHECK_WORK)
            ),
            lastCheck = storeRepository.lastUpdateCheck()?.let { summary ->
                "${DateFormat.getDateTimeInstance().format(Date(summary.finishedAt))} — " +
                    "проверено ${summary.checked}, обновлений ${summary.updates}, " +
                    "проблем ${summary.problems}"
            },
            settings = listOf(
                "wifiOnly" to settings.wifiOnly.toString(),
                "showExcludedUpdates" to settings.showExcludedUpdates.toString(),
                "allowMobileData" to settings.allowMobileData.toString(),
                "requiresCharging" to settings.requiresCharging.toString(),
                "updateIntervalHours" to settings.updateIntervalHours.toString(),
                "autoDownloadUpdates" to settings.autoDownloadUpdates.toString(),
                "autoInstallUpdates" to settings.autoInstallUpdates.toString(),
                "autoInstallNewApps" to settings.autoInstallNewApps.toString(),
                "silentUpdatesEnabled" to settings.silentUpdatesEnabled.toString(),
                "selfUpdateEnabled" to settings.selfUpdateEnabled.toString(),
                "githubEnabled" to settings.githubEnabled.toString(),
                "respectBatterySaver" to settings.respectBatterySaver.toString(),
                "queueMode" to settings.queueMode.name
            ),
            events = EventLog(appContext).read()
        )
    }

    /**
     * WorkManager's own account of one unique work name.
     *
     * Everything the store records about a transfer is written from inside the worker, so a failure
     * that happens before the worker starts leaves no trace of any kind - the row simply waits,
     * which in a report is indistinguishable from a row that is genuinely next in line. The state
     * and the attempt count come from the system and cover exactly that gap.
     */
    private fun describeWork(uniqueName: String): String = runCatching {
        // Bounded on purpose. A report is collected because something is already wrong, and an
        // unbounded get() on a database that is not answering would leave the button spinning
        // forever on exactly the devices this report exists to describe. A line saying the answer
        // did not arrive is worth more than no report at all.
        val infos = WorkManager.getInstance(appContext)
            .getWorkInfosForUniqueWork(uniqueName)
            .get(2, TimeUnit.SECONDS)
        if (infos.isEmpty()) {
            "нет"
        } else {
            infos.joinToString("; ") { info ->
                buildString {
                    append(info.state.name)
                    append(", попыток ")
                    append(info.runAttemptCount)
                    if (info.state == WorkInfo.State.ENQUEUED &&
                        info.stopReason != WorkInfo.STOP_REASON_NOT_STOPPED
                    ) {
                        append(", остановлена системой: ")
                        append(info.stopReason)
                    }
                }
            }
        }
    }.getOrElse { "не удалось прочитать" }

    /**
     * What the network looks like to the constraints a transfer carries.
     *
     * A Wi-Fi-only download waits for an unmetered network, and a phone that reports its Wi-Fi as
     * metered stalls the queue in a way that looks identical to every other stall. Whether there is
     * a connection at all, and whether the system calls it metered, is the difference.
     */
    private fun describeNetwork(): String = runCatching {
        val manager = appContext.getSystemService(ConnectivityManager::class.java)
            ?: return@runCatching "неизвестно"
        val network = manager.activeNetwork ?: return@runCatching "нет подключения"
        val capabilities = manager.getNetworkCapabilities(network)
            ?: return@runCatching "нет подключения"
        val transport = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "мобильная"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "другая"
        }
        val metered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        "$transport, лимитная: ${if (metered) "да" else "нет"}" +
            if (validated) "" else ", без доступа в интернет"
    }.getOrElse { "неизвестно" }

    /** Data Saver stops background traffic on a metered network, and says nothing while it does. */
    private fun describeDataSaver(): String = runCatching {
        val manager = appContext.getSystemService(ConnectivityManager::class.java)
            ?: return@runCatching "неизвестно"
        when (manager.restrictBackgroundStatus) {
            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED -> "выкл"
            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED -> "вкл, приложение в исключениях"
            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED -> "вкл, фоновый трафик запрещён"
            else -> "неизвестно"
        }
    }.getOrElse { "неизвестно" }

    /**
     * Whether each channel a background notification needs is still switched on.
     *
     * Below Android 12 a transfer the user starts runs as a foreground service, which means it
     * needs its notification; a channel the user turned off can therefore stop a download rather
     * than just silence it.
     */
    private fun describeNotificationChannels(): List<Pair<String, String>> = runCatching {
        val manager = appContext.getSystemService(NotificationManager::class.java)
            ?: return@runCatching emptyList()
        listOf(
            "канал передач" to NotificationCoordinator.CHANNEL_TRANSFERS,
            "канал проверок" to NotificationCoordinator.CHANNEL_CHECKS,
            "канал готовых обновлений" to NotificationCoordinator.CHANNEL_READY,
            "канал ошибок" to NotificationCoordinator.CHANNEL_ERRORS
        ).map { (label, id) ->
            val channel = manager.getNotificationChannel(id)
            label to when {
                channel == null -> "не создан"
                channel.importance == NotificationManager.IMPORTANCE_NONE -> "выключен"
                else -> "вкл (важность ${channel.importance})"
            }
        }
    }.getOrElse { emptyList() }

    /**
     * The bucket the system has put this app in.
     *
     * RARE and RESTRICTED mean jobs run rarely or barely at all, which turns "checks every six
     * hours" into "checks whenever the system feels like it" and looks, from inside the app, like
     * the schedule simply being ignored.
     */
    private fun describeStandbyBucket(): String = runCatching {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return@runCatching "не применимо"
        val manager = appContext.getSystemService(UsageStatsManager::class.java)
            ?: return@runCatching "неизвестно"
        when (val bucket = manager.appStandbyBucket) {
            UsageStatsManager.STANDBY_BUCKET_ACTIVE -> "ACTIVE"
            UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> "WORKING_SET"
            UsageStatsManager.STANDBY_BUCKET_FREQUENT -> "FREQUENT"
            UsageStatsManager.STANDBY_BUCKET_RARE -> "RARE"
            UsageStatsManager.STANDBY_BUCKET_RESTRICTED -> "RESTRICTED"
            else -> bucket.toString()
        }
    }.getOrElse { "неизвестно" }
}
