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
import dev.wystore.permissions.VendorBackgroundSettings
import dev.wystore.root.RootInstaller
import dev.wystore.updates.QueueRepository
import dev.wystore.updates.SessionInstallSupport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
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
 * Everything else is read locally: no request leaves the device to build a report, because a report
 * is asked for when something is already wrong and a button that needs the network to explain why
 * the network is not working helps nobody. Every question put to the system is wrapped, so one
 * unavailable service cannot be the reason the whole report fails to appear.
 */
class DiagnosticsCollector(context: Context) {

    private val appContext = context.applicationContext

    suspend fun collect(): Diagnostics = withContext(Dispatchers.IO) {
        val storeRepository = StoreRepository(appContext)
        val settings = storeRepository.settings()
        val permissions = PermissionRepository(appContext).snapshot()
        val metrics = appContext.resources.displayMetrics
        val configuration = appContext.resources.configuration
        val queueRepository = runCatching { QueueRepository.getInstance(appContext) }.getOrNull()
        val queue = queueRepository
            ?.let { repository -> runCatching { repository.snapshotAll() }.getOrNull() }
            .orEmpty()
        val pending = queueRepository
            ?.let { repository -> runCatching { repository.getPendingUpdates() }.getOrNull() }
            .orEmpty()
        val managed = runCatching { storeRepository.managedApps() }.getOrDefault(emptyList())
        val workStates = readWorkStates(
            queue.map { TransferDispatcher.downloadWorkName(it.id) } + listOf(
                UpdateWorkScheduler.MANUAL_CHECK_WORK,
                UpdateWorkScheduler.PERIODIC_CHECK_WORK
            )
        )

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
            artifactBytes = runCatching {
                File(appContext.filesDir, ARTIFACT_DIRECTORY).walkBottomUp()
                    .filter { it.isFile }
                    .sumOf { it.length() }
            }.getOrDefault(-1L),
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
            vendorBackgroundSettings = runCatching {
                VendorBackgroundSettings.describe(appContext)
            }.getOrDefault("неизвестно"),
            sessionsRefusedByFirmware = SessionInstallSupport(appContext).sessionsRefused(),
            firmwareShell = runCatching { FirmwareShell.describe(FirmwareShell::systemProperty) }
                .getOrDefault("неизвестно"),
            managedApps = managed.size,
            managedBySource = managed
                .groupingBy { it.source?.name ?: "не указан" }
                .eachCount()
                .toList()
                .sortedByDescending { (_, count) -> count },
            managedWithoutAutoUpdate = managed.count { !it.autoUpdate },
            managedForcedToStore = managed.count { it.forceWyStore },
            githubRepositories = runCatching { storeRepository.githubRepositories().size }
                .getOrDefault(0),
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
                        "задача: " + (
                            workStates[TransferDispatcher.downloadWorkName(item.id)] ?: "нет"
                            )
                    )
                )
            },
            downloadedNotInstalled = pending.map { update ->
                "${update.packageName} ${update.versionName} (${update.source.name}), " +
                    "файлов ${update.filePaths.size}, " +
                    "скачано ${DateFormat.getDateTimeInstance().format(Date(update.downloadedAt))}"
            },
            backgroundWork = listOf(
                "проверка (по кнопке)" to
                    (workStates[UpdateWorkScheduler.MANUAL_CHECK_WORK] ?: "нет"),
                "проверка (по расписанию)" to
                    (workStates[UpdateWorkScheduler.PERIODIC_CHECK_WORK] ?: "нет")
            ),
            lastCheck = storeRepository.lastUpdateCheck()?.let { summary ->
                "${DateFormat.getDateTimeInstance().format(Date(summary.finishedAt))} — " +
                    (if (summary.manual) "по кнопке" else "по расписанию") + ", " +
                    "проверено ${summary.checked} из ${summary.total}, " +
                    "обновлений ${summary.updates}, проблем ${summary.problems}"
            },
            lastCheckProblems = storeRepository.lastUpdateCheck()?.problemApps.orEmpty()
                .map { problem -> "${problem.packageName} — ${problem.reason.name}" },
            settings = DiagnosticsSettingsDump.of(settings),
            events = EventLog(appContext).read()
        )
    }

    /**
     * WorkManager's own account of every task this report mentions, by unique work name.
     *
     * Everything the store records about a transfer is written from inside the worker, so a failure
     * that happens before the worker starts leaves no trace of any kind - the row simply waits,
     * which in a report is indistinguishable from a row that is genuinely next in line. The state
     * and the attempt count come from the system and cover exactly that gap.
     *
     * Asked name by name, because a unique work name is not one of a request's tags and a query
     * across tags cannot say which name each result belongs to. Every lookup is bounded: a report
     * is asked for when something is already wrong, and a line saying the answer never arrived
     * beats a button that spins forever.
     */
    private fun readWorkStates(names: List<String>): Map<String, String> {
        val manager = runCatching { WorkManager.getInstance(appContext) }.getOrNull()
            ?: return emptyMap()
        return names.associateWith { name ->
            runCatching {
                val infos = manager.getWorkInfosForUniqueWork(name).get(2, TimeUnit.SECONDS)
                if (infos.isEmpty()) {
                    "нет"
                } else {
                    infos.joinToString("; ") { describeWorkInfo(it) }
                }
            }.getOrElse { "не удалось прочитать" }
        }
    }

    private fun describeWorkInfo(info: WorkInfo): String = buildString {
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
        val vpn = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        "$transport, лимитная: ${if (metered) "да" else "нет"}" +
            (if (validated) "" else ", без доступа в интернет") +
            (if (vpn && transport != "VPN") ", через VPN" else "")
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
     * than just silence it. Above it, a channel that is off is why "nothing told me" happens.
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

    private companion object {
        const val ARTIFACT_DIRECTORY = "pending_updates"
    }
}
