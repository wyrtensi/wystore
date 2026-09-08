package dev.wystore.settings

import dev.wystore.data.StoreSettings
import dev.wystore.updates.model.QueueMode

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class AppLanguage { SYSTEM, RU, EN }

data class AppSettings(
    val wifiOnly: Boolean = true,
    /** Off by default: an update that waits for a charger is an update that never arrives. */
    val requiresCharging: Boolean = false,
    val allowMobileData: Boolean = false,
    val rootBackgroundDownloadsEnabled: Boolean = false,
    val rootSilentInstallEnabled: Boolean = false,
    val updateIntervalHours: Long = 24,
    val queueMode: QueueMode = QueueMode.SMART_PROMPTS,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val dynamicColorEnabled: Boolean = true,
    val githubEnabled: Boolean = true,
    val readyNotificationsEnabled: Boolean = true,
    val errorNotificationsEnabled: Boolean = true,
    val checkSummaryNotificationsEnabled: Boolean = false,
    val quietHoursEnabled: Boolean = false,
    val quietHoursStart: Int = 23,
    val quietHoursEnd: Int = 8,
    val respectBatterySaver: Boolean = true,
    val selfUpdateEnabled: Boolean = true,
    /** Update an app Wy Store installed without stopping on Android's dialog. */
    val silentUpdatesEnabled: Boolean = true,
    /** Fetch an update as soon as a check finds it, instead of waiting to be asked. */
    val autoDownloadUpdates: Boolean = true,
    /** Hand a downloaded update straight to the installer instead of waiting for a tap. */
    val autoInstallUpdates: Boolean = true,
    /** The same for an app being installed for the first time. */
    val autoInstallNewApps: Boolean = true,
    val artifactRetentionDays: Int = 7,
    val artifactStorageLimitMb: Int = 2_048
)

fun AppSettings.toStoreSettings(): StoreSettings = StoreSettings(
    wifiOnly = wifiOnly,
    requiresCharging = requiresCharging,
    allowMobileData = allowMobileData,
    backgroundRootUpdates = rootSilentInstallEnabled,
    updateIntervalHours = updateIntervalHours,
    queueMode = queueMode,
    rootBackgroundDownloadsEnabled = rootBackgroundDownloadsEnabled,
    rootSilentInstallEnabled = rootSilentInstallEnabled,
    themeMode = themeMode,
    language = language,
    dynamicColorEnabled = dynamicColorEnabled,
    githubEnabled = githubEnabled,
    readyNotificationsEnabled = readyNotificationsEnabled,
    errorNotificationsEnabled = errorNotificationsEnabled,
    checkSummaryNotificationsEnabled = checkSummaryNotificationsEnabled,
    quietHoursEnabled = quietHoursEnabled,
    quietHoursStart = quietHoursStart,
    quietHoursEnd = quietHoursEnd,
    respectBatterySaver = respectBatterySaver,
    selfUpdateEnabled = selfUpdateEnabled,
    silentUpdatesEnabled = silentUpdatesEnabled,
    autoDownloadUpdates = autoDownloadUpdates,
    autoInstallUpdates = autoInstallUpdates,
    autoInstallNewApps = autoInstallNewApps,
    artifactRetentionDays = artifactRetentionDays,
    artifactStorageLimitMb = artifactStorageLimitMb
)

fun StoreSettings.toAppSettings(): AppSettings = AppSettings(
    wifiOnly = wifiOnly,
    requiresCharging = requiresCharging,
    allowMobileData = allowMobileData,
    rootBackgroundDownloadsEnabled = rootBackgroundDownloadsEnabled,
    rootSilentInstallEnabled = rootSilentInstallEnabled,
    updateIntervalHours = updateIntervalHours,
    queueMode = queueMode,
    themeMode = themeMode,
    language = language,
    dynamicColorEnabled = dynamicColorEnabled,
    githubEnabled = githubEnabled,
    readyNotificationsEnabled = readyNotificationsEnabled,
    errorNotificationsEnabled = errorNotificationsEnabled,
    checkSummaryNotificationsEnabled = checkSummaryNotificationsEnabled,
    quietHoursEnabled = quietHoursEnabled,
    quietHoursStart = quietHoursStart,
    quietHoursEnd = quietHoursEnd,
    respectBatterySaver = respectBatterySaver,
    selfUpdateEnabled = selfUpdateEnabled,
    silentUpdatesEnabled = silentUpdatesEnabled,
    autoDownloadUpdates = autoDownloadUpdates,
    autoInstallUpdates = autoInstallUpdates,
    autoInstallNewApps = autoInstallNewApps,
    artifactRetentionDays = artifactRetentionDays,
    artifactStorageLimitMb = artifactStorageLimitMb
)
