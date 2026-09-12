package dev.wystore.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.wystore.updates.model.QueueMode

/**
 * Reads and writes [AppSettings] as DataStore preferences.
 *
 * The rule this file exists to keep: **a key is written only when its value actually changes.**
 *
 * Saving one switch used to persist all twenty-five keys, which quietly turned every untouched
 * default into a stored answer. From then on the phone was pinned to whatever the defaults were on
 * the day its owner first opened Settings: a later, better default could never reach it, and the
 * only way to deliver one was to overwrite the record — which overwrites a deliberate choice just
 * as readily as an inherited default.
 *
 * Leaving an untouched key absent keeps the two apart. An absent key means "no opinion" and follows
 * the default in [read], so a changed default arrives with the next update on its own; a key that is
 * present was set by the user and is never touched again.
 */
internal object SettingsCodec {

    val KEY_WIFI_ONLY = booleanPreferencesKey("wifi_only")
    val KEY_REQUIRES_CHARGING = booleanPreferencesKey("requires_charging")
    val KEY_ALLOW_MOBILE_DATA = booleanPreferencesKey("allow_mobile_data")
    val KEY_ROOT_BG_DOWNLOADS = booleanPreferencesKey("root_background_downloads")
    val KEY_ROOT_SILENT_INSTALL = booleanPreferencesKey("root_silent_install")
    val KEY_UPDATE_INTERVAL_HOURS = longPreferencesKey("interval_hours")
    val KEY_QUEUE_MODE = stringPreferencesKey("queue_mode")
    val KEY_SEARCH_SOURCES = stringPreferencesKey("search_sources")
    val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
    val KEY_LANGUAGE = stringPreferencesKey("language")
    val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    val KEY_GITHUB_ENABLED = booleanPreferencesKey("github_enabled")
    val KEY_READY_NOTIFICATIONS = booleanPreferencesKey("ready_notifications")
    val KEY_ERROR_NOTIFICATIONS = booleanPreferencesKey("error_notifications")
    val KEY_CHECK_SUMMARY_NOTIFICATIONS = booleanPreferencesKey("check_summary_notifications")
    val KEY_QUIET_HOURS_ENABLED = booleanPreferencesKey("quiet_hours_enabled")
    val KEY_QUIET_HOURS_START = intPreferencesKey("quiet_hours_start")
    val KEY_QUIET_HOURS_END = intPreferencesKey("quiet_hours_end")
    val KEY_RESPECT_BATTERY_SAVER = booleanPreferencesKey("respect_battery_saver")
    val KEY_SELF_UPDATE_ENABLED = booleanPreferencesKey("self_update_enabled")
    val KEY_SILENT_UPDATES = booleanPreferencesKey("silent_updates")
    val KEY_AUTO_DOWNLOAD_UPDATES = booleanPreferencesKey("auto_download_updates")
    val KEY_AUTO_INSTALL_UPDATES = booleanPreferencesKey("auto_install_updates")
    val KEY_AUTO_INSTALL_NEW_APPS = booleanPreferencesKey("auto_install_new_apps")
    val KEY_SHOW_EXCLUDED_UPDATES = booleanPreferencesKey("show_excluded_updates")
    val KEY_SOURCE_CATEGORIES = booleanPreferencesKey("source_categories")
    val KEY_ARTIFACT_RETENTION_DAYS = intPreferencesKey("retention_days")
    val KEY_ARTIFACT_STORAGE_LIMIT_MB = intPreferencesKey("storage_limit_mb")

    // Names that StoreRepository used in SharedPreferences before DataStore became the single
    // source of truth. The SharedPreferences migration copies keys verbatim, so these are what an
    // upgrading install actually has on disk; without the fallbacks below every switch silently
    // reverts to its default.

    /** An absent key takes the default from [AppSettings], which is where defaults are declared. */
    fun read(prefs: Preferences): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            wifiOnly = prefs[KEY_WIFI_ONLY] ?: defaults.wifiOnly,
            requiresCharging = prefs[KEY_REQUIRES_CHARGING] ?: defaults.requiresCharging,
            allowMobileData = prefs[KEY_ALLOW_MOBILE_DATA] ?: defaults.allowMobileData,
            rootBackgroundDownloadsEnabled = prefs[KEY_ROOT_BG_DOWNLOADS]
                ?: defaults.rootBackgroundDownloadsEnabled,
            rootSilentInstallEnabled = prefs[KEY_ROOT_SILENT_INSTALL]
                ?: defaults.rootSilentInstallEnabled,
            updateIntervalHours = prefs[KEY_UPDATE_INTERVAL_HOURS] ?: defaults.updateIntervalHours,
            queueMode = prefs[KEY_QUEUE_MODE]?.let { raw ->
                runCatching { QueueMode.valueOf(raw) }.getOrNull()
            } ?: defaults.queueMode,
            themeMode = prefs[KEY_THEME_MODE]?.let { raw ->
                runCatching { ThemeMode.valueOf(raw) }.getOrNull()
            } ?: defaults.themeMode,
            language = prefs[KEY_LANGUAGE]?.let { raw ->
                runCatching { AppLanguage.valueOf(raw) }.getOrNull()
            } ?: defaults.language,
            dynamicColorEnabled = prefs[KEY_DYNAMIC_COLOR] ?: defaults.dynamicColorEnabled,
            githubEnabled = prefs[KEY_GITHUB_ENABLED] ?: defaults.githubEnabled,
            readyNotificationsEnabled = prefs[KEY_READY_NOTIFICATIONS]
                ?: defaults.readyNotificationsEnabled,
            errorNotificationsEnabled = prefs[KEY_ERROR_NOTIFICATIONS]
                ?: defaults.errorNotificationsEnabled,
            checkSummaryNotificationsEnabled = prefs[KEY_CHECK_SUMMARY_NOTIFICATIONS]
                ?: defaults.checkSummaryNotificationsEnabled,
            quietHoursEnabled = prefs[KEY_QUIET_HOURS_ENABLED] ?: defaults.quietHoursEnabled,
            quietHoursStart = prefs[KEY_QUIET_HOURS_START] ?: defaults.quietHoursStart,
            quietHoursEnd = prefs[KEY_QUIET_HOURS_END] ?: defaults.quietHoursEnd,
            respectBatterySaver = prefs[KEY_RESPECT_BATTERY_SAVER] ?: defaults.respectBatterySaver,
            selfUpdateEnabled = prefs[KEY_SELF_UPDATE_ENABLED] ?: defaults.selfUpdateEnabled,
            silentUpdatesEnabled = prefs[KEY_SILENT_UPDATES] ?: defaults.silentUpdatesEnabled,
            autoDownloadUpdates = prefs[KEY_AUTO_DOWNLOAD_UPDATES] ?: defaults.autoDownloadUpdates,
            autoInstallUpdates = prefs[KEY_AUTO_INSTALL_UPDATES] ?: defaults.autoInstallUpdates,
            autoInstallNewApps = prefs[KEY_AUTO_INSTALL_NEW_APPS] ?: defaults.autoInstallNewApps,
            showExcludedUpdates = prefs[KEY_SHOW_EXCLUDED_UPDATES] ?: defaults.showExcludedUpdates,
            sourceCategories = prefs[KEY_SOURCE_CATEGORIES] ?: defaults.sourceCategories,
            searchSources = prefs[KEY_SEARCH_SOURCES]?.let { raw ->
                runCatching { dev.wystore.data.SearchSources.valueOf(raw) }.getOrNull()
            } ?: defaults.searchSources,
            artifactRetentionDays = prefs[KEY_ARTIFACT_RETENTION_DAYS] ?: defaults.artifactRetentionDays,
            artifactStorageLimitMb = prefs[KEY_ARTIFACT_STORAGE_LIMIT_MB] ?: defaults.artifactStorageLimitMb
        )
    }

    /**
     * Persists the difference between [current] - what [read] returns right now - and [updated].
     *
     * [current] has to be the effective settings rather than the raw file, so a field the user
     * never touched compares equal to its default and stays out of the file.
     */
    fun write(prefs: MutablePreferences, current: AppSettings, updated: AppSettings) {
        prefs.put(KEY_WIFI_ONLY, updated.wifiOnly, current.wifiOnly)
        prefs.put(KEY_REQUIRES_CHARGING, updated.requiresCharging, current.requiresCharging)
        prefs.put(KEY_ALLOW_MOBILE_DATA, updated.allowMobileData, current.allowMobileData)
        prefs.put(
            KEY_ROOT_BG_DOWNLOADS,
            updated.rootBackgroundDownloadsEnabled,
            current.rootBackgroundDownloadsEnabled
        )
        prefs.put(
            KEY_ROOT_SILENT_INSTALL,
            updated.rootSilentInstallEnabled,
            current.rootSilentInstallEnabled
        )
        prefs.put(KEY_UPDATE_INTERVAL_HOURS, updated.updateIntervalHours, current.updateIntervalHours)
        prefs.put(KEY_QUEUE_MODE, updated.queueMode.name, current.queueMode.name)
        prefs.put(KEY_THEME_MODE, updated.themeMode.name, current.themeMode.name)
        prefs.put(KEY_LANGUAGE, updated.language.name, current.language.name)
        prefs.put(KEY_DYNAMIC_COLOR, updated.dynamicColorEnabled, current.dynamicColorEnabled)
        prefs.put(KEY_GITHUB_ENABLED, updated.githubEnabled, current.githubEnabled)
        prefs.put(
            KEY_READY_NOTIFICATIONS,
            updated.readyNotificationsEnabled,
            current.readyNotificationsEnabled
        )
        prefs.put(
            KEY_ERROR_NOTIFICATIONS,
            updated.errorNotificationsEnabled,
            current.errorNotificationsEnabled
        )
        prefs.put(
            KEY_CHECK_SUMMARY_NOTIFICATIONS,
            updated.checkSummaryNotificationsEnabled,
            current.checkSummaryNotificationsEnabled
        )
        prefs.put(KEY_QUIET_HOURS_ENABLED, updated.quietHoursEnabled, current.quietHoursEnabled)
        prefs.put(KEY_QUIET_HOURS_START, updated.quietHoursStart, current.quietHoursStart)
        prefs.put(KEY_QUIET_HOURS_END, updated.quietHoursEnd, current.quietHoursEnd)
        prefs.put(KEY_RESPECT_BATTERY_SAVER, updated.respectBatterySaver, current.respectBatterySaver)
        prefs.put(KEY_SELF_UPDATE_ENABLED, updated.selfUpdateEnabled, current.selfUpdateEnabled)
        prefs.put(KEY_SILENT_UPDATES, updated.silentUpdatesEnabled, current.silentUpdatesEnabled)
        prefs.put(KEY_AUTO_DOWNLOAD_UPDATES, updated.autoDownloadUpdates, current.autoDownloadUpdates)
        prefs.put(KEY_AUTO_INSTALL_UPDATES, updated.autoInstallUpdates, current.autoInstallUpdates)
        prefs.put(KEY_AUTO_INSTALL_NEW_APPS, updated.autoInstallNewApps, current.autoInstallNewApps)
        prefs.put(KEY_SHOW_EXCLUDED_UPDATES, updated.showExcludedUpdates, current.showExcludedUpdates)
        prefs.put(KEY_SOURCE_CATEGORIES, updated.sourceCategories, current.sourceCategories)
        prefs.put(KEY_SEARCH_SOURCES, updated.searchSources.name, current.searchSources.name)
        prefs.put(
            KEY_ARTIFACT_RETENTION_DAYS,
            updated.artifactRetentionDays,
            current.artifactRetentionDays
        )
        prefs.put(
            KEY_ARTIFACT_STORAGE_LIMIT_MB,
            updated.artifactStorageLimitMb,
            current.artifactStorageLimitMb
        )
    }

    private fun <T : Any> MutablePreferences.put(key: Preferences.Key<T>, updated: T, current: T) {
        if (updated != current) {
            this[key] = updated
        }
    }
}
