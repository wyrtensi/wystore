package dev.wystore.data

/**
 * Every setting, spelled out for a bug report.
 *
 * The first version of this listed whichever thirteen mattered the day it was written, and each
 * setting added afterwards was simply absent from every report anyone sent - including the switches
 * that decide whether a notification is posted at all. A report that quietly omits the setting
 * causing the problem is worse than no report.
 *
 * Written by hand rather than reflected over, so that what a reader sees is what the code says and
 * the keys stay the names people can search for. Keeping up is not left to whoever remembers:
 * [dev.wystore.data.StoreSettingsCoverageTest] compares this list against the fields of
 * [StoreSettings] and fails when one gains a field this does not print.
 *
 * It lives apart from the collector because the collector needs a device and this needs nothing.
 */
object DiagnosticsSettingsDump {

    fun of(settings: StoreSettings): List<Pair<String, String>> = listOf(
        "wifiOnly" to settings.wifiOnly.toString(),
        "requiresCharging" to settings.requiresCharging.toString(),
        "allowMobileData" to settings.allowMobileData.toString(),
        "backgroundRootUpdates" to settings.backgroundRootUpdates.toString(),
        "updateIntervalHours" to settings.updateIntervalHours.toString(),
        "queueMode" to settings.queueMode.name,
        "rootBackgroundDownloadsEnabled" to settings.rootBackgroundDownloadsEnabled.toString(),
        "rootSilentInstallEnabled" to settings.rootSilentInstallEnabled.toString(),
        "themeMode" to settings.themeMode.name,
        "language" to settings.language.name,
        "dynamicColorEnabled" to settings.dynamicColorEnabled.toString(),
        "githubEnabled" to settings.githubEnabled.toString(),
        "readyNotificationsEnabled" to settings.readyNotificationsEnabled.toString(),
        "errorNotificationsEnabled" to settings.errorNotificationsEnabled.toString(),
        "checkSummaryNotificationsEnabled" to settings.checkSummaryNotificationsEnabled.toString(),
        "quietHoursEnabled" to settings.quietHoursEnabled.toString(),
        "quietHoursStart" to settings.quietHoursStart.toString(),
        "quietHoursEnd" to settings.quietHoursEnd.toString(),
        "respectBatterySaver" to settings.respectBatterySaver.toString(),
        "selfUpdateEnabled" to settings.selfUpdateEnabled.toString(),
        "silentUpdatesEnabled" to settings.silentUpdatesEnabled.toString(),
        "autoDownloadUpdates" to settings.autoDownloadUpdates.toString(),
        "sourceCategories" to settings.sourceCategories.toString(),
        "showExcludedUpdates" to settings.showExcludedUpdates.toString(),
        "autoInstallUpdates" to settings.autoInstallUpdates.toString(),
        "autoInstallNewApps" to settings.autoInstallNewApps.toString(),
        "artifactRetentionDays" to settings.artifactRetentionDays.toString(),
        "artifactStorageLimitMb" to settings.artifactStorageLimitMb.toString()
    )
}
