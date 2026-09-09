package dev.wystore.data

import android.content.Context
import android.os.Build
import dev.wystore.BuildConfig
import dev.wystore.permissions.PermissionRepository
import dev.wystore.root.RootInstaller
import dev.wystore.updates.QueueRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/**
 * Gathers what a bug report needs from the device and from the store's own state.
 *
 * Root is checked here rather than remembered, because "is root available" is a question with a
 * different answer after the user grants or revokes it, and a report that says something stale is
 * worse than one that says nothing. It runs a shell command, so this is only ever called when
 * somebody asks for a report.
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
            rootAvailable = runCatching { RootInstaller().isAvailable() }.getOrNull(),
            rootSilentInstall = settings.rootSilentInstallEnabled,
            rootBackgroundDownloads = settings.rootBackgroundDownloadsEnabled,
            notificationsGranted = permissions.notificationsGranted,
            canInstallUnknownApps = permissions.canInstallUnknownApps,
            batteryOptimizationsIgnored = permissions.batteryOptimizationsIgnored,
            managedApps = storeRepository.managedApps().size,
            githubRepositories = storeRepository.githubRepositories().size,
            queueRows = queue.map { item ->
                "${item.packageName} ${item.versionName} ${item.state}" +
                    (item.errorCode?.let { " ${it.name}" } ?: "") +
                    (item.errorDetail?.let { ": $it" } ?: "")
            },
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
}
