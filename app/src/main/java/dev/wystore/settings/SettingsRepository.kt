package dev.wystore.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.wystore.updates.model.QueueMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.IOException

/**
 * Settings keys this migration is allowed to take out of the legacy SharedPreferences.
 *
 * The list must be explicit. `SharedPreferencesMigration` with no `keysToMigrate` migrates *every*
 * key in the file and then **deletes them**, and `wy_store` also holds the managed-app list, the
 * saved GitHub repositories, the RuStore compatibility record and the last-check summary. Without
 * this restriction the migration silently wiped all of them — which is why an app adopted or
 * installed through Wy Store disappeared from the library after the next restart.
 */
internal val LEGACY_SETTINGS_KEYS = setOf(
    "wifi_only",
    "requires_charging",
    "allow_mobile",
    "allow_mobile_data",
    "background_root",
    "root_bg_downloads",
    "root_background_downloads",
    "root_silent_install",
    "interval_hours",
    "queue_mode",
    "theme_mode",
    "language",
    "dynamic_color",
    "github_enabled",
    "notif_ready",
    "notif_error",
    "notif_summary",
    "ready_notifications",
    "error_notifications",
    "check_summary_notifications",
    "retention_days",
    "storage_limit_mb"
)

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "wystore_settings",
    produceMigrations = { context ->
        listOf(
            SharedPreferencesMigration(
                context = context,
                sharedPreferencesName = "wy_store",
                keysToMigrate = LEGACY_SETTINGS_KEYS
            )
        )
    }
)

class SettingsRepository(private val context: Context) {

    private val dataStore = context.applicationContext.settingsDataStore

    companion object Keys {
        val KEY_WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val KEY_REQUIRES_CHARGING = booleanPreferencesKey("requires_charging")
        val KEY_ALLOW_MOBILE_DATA = booleanPreferencesKey("allow_mobile_data")
        val KEY_ROOT_BG_DOWNLOADS = booleanPreferencesKey("root_background_downloads")
        val KEY_ROOT_SILENT_INSTALL = booleanPreferencesKey("root_silent_install")
        val KEY_UPDATE_INTERVAL_HOURS = longPreferencesKey("interval_hours")
        val KEY_QUEUE_MODE = stringPreferencesKey("queue_mode")
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
        val KEY_DEFAULTS_REVISION = intPreferencesKey("defaults_revision")
        val KEY_SILENT_UPDATES = booleanPreferencesKey("silent_updates")
        val KEY_AUTO_DOWNLOAD_UPDATES = booleanPreferencesKey("auto_download_updates")
        val KEY_AUTO_INSTALL_UPDATES = booleanPreferencesKey("auto_install_updates")
        val KEY_AUTO_INSTALL_NEW_APPS = booleanPreferencesKey("auto_install_new_apps")
        val KEY_ARTIFACT_RETENTION_DAYS = intPreferencesKey("retention_days")
        val KEY_ARTIFACT_STORAGE_LIMIT_MB = intPreferencesKey("storage_limit_mb")

        // Names that StoreRepository used in SharedPreferences before DataStore became the single
        // source of truth. The SharedPreferences migration copies keys verbatim, so these are what an
        // upgrading install actually has on disk; without the fallbacks below every switch silently
        // reverts to its default.
        val KEY_LEGACY_BG_ROOT = booleanPreferencesKey("background_root")
        val KEY_LEGACY_ALLOW_MOBILE = booleanPreferencesKey("allow_mobile")
        val KEY_LEGACY_ROOT_BG_DOWNLOADS = booleanPreferencesKey("root_bg_downloads")
        val KEY_LEGACY_NOTIF_READY = booleanPreferencesKey("notif_ready")
        val KEY_LEGACY_NOTIF_ERROR = booleanPreferencesKey("notif_error")
        val KEY_LEGACY_NOTIF_SUMMARY = booleanPreferencesKey("notif_summary")
    }

    private fun Preferences.readSettings(): AppSettings = AppSettings(
        wifiOnly = this[KEY_WIFI_ONLY] ?: this[KEY_LEGACY_ALLOW_MOBILE]?.not() ?: true,
        requiresCharging = this[KEY_REQUIRES_CHARGING] ?: false,
        allowMobileData = this[KEY_ALLOW_MOBILE_DATA] ?: this[KEY_LEGACY_ALLOW_MOBILE] ?: false,
        rootBackgroundDownloadsEnabled = this[KEY_ROOT_BG_DOWNLOADS]
            ?: this[KEY_LEGACY_ROOT_BG_DOWNLOADS] ?: false,
        rootSilentInstallEnabled = this[KEY_ROOT_SILENT_INSTALL] ?: this[KEY_LEGACY_BG_ROOT] ?: false,
        updateIntervalHours = this[KEY_UPDATE_INTERVAL_HOURS] ?: 24L,
        queueMode = this[KEY_QUEUE_MODE]?.let { raw ->
            runCatching { QueueMode.valueOf(raw) }.getOrNull()
        } ?: QueueMode.SMART_PROMPTS,
        themeMode = this[KEY_THEME_MODE]?.let { raw ->
            runCatching { ThemeMode.valueOf(raw) }.getOrNull()
        } ?: ThemeMode.SYSTEM,
        language = this[KEY_LANGUAGE]?.let { raw ->
            runCatching { AppLanguage.valueOf(raw) }.getOrNull()
        } ?: AppLanguage.SYSTEM,
        dynamicColorEnabled = this[KEY_DYNAMIC_COLOR] ?: true,
        githubEnabled = this[KEY_GITHUB_ENABLED] ?: true,
        readyNotificationsEnabled = this[KEY_READY_NOTIFICATIONS] ?: this[KEY_LEGACY_NOTIF_READY] ?: true,
        errorNotificationsEnabled = this[KEY_ERROR_NOTIFICATIONS] ?: this[KEY_LEGACY_NOTIF_ERROR] ?: true,
        checkSummaryNotificationsEnabled = this[KEY_CHECK_SUMMARY_NOTIFICATIONS]
            ?: this[KEY_LEGACY_NOTIF_SUMMARY] ?: false,
        quietHoursEnabled = this[KEY_QUIET_HOURS_ENABLED] ?: false,
        quietHoursStart = this[KEY_QUIET_HOURS_START] ?: 23,
        quietHoursEnd = this[KEY_QUIET_HOURS_END] ?: 8,
        respectBatterySaver = this[KEY_RESPECT_BATTERY_SAVER] ?: true,
        selfUpdateEnabled = this[KEY_SELF_UPDATE_ENABLED] ?: true,
        silentUpdatesEnabled = this[KEY_SILENT_UPDATES] ?: true,
        autoDownloadUpdates = this[KEY_AUTO_DOWNLOAD_UPDATES] ?: true,
        autoInstallUpdates = this[KEY_AUTO_INSTALL_UPDATES] ?: true,
        autoInstallNewApps = this[KEY_AUTO_INSTALL_NEW_APPS] ?: true,
        artifactRetentionDays = this[KEY_ARTIFACT_RETENTION_DAYS] ?: 7,
        artifactStorageLimitMb = this[KEY_ARTIFACT_STORAGE_LIMIT_MB] ?: 2_048
    )

    val settings: Flow<AppSettings> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs -> prefs.readSettings() }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs ->
            val current = prefs.readSettings()
            prefs.writeSettings(transform(current))
        }
    }

    /**
     * Raises the defaults on a phone whose settings were written by an earlier version.
     *
     * Runs once per revision at startup. Without it a changed default reaches new installs only:
     * every key is written whenever any setting is saved, so the old value is already on disk.
     */
    suspend fun applyDefaultRevision() {
        dataStore.edit { prefs ->
            val stored = prefs[KEY_DEFAULTS_REVISION] ?: 0
            if (!SettingsDefaultsMigration.needsUpgrade(stored)) return@edit
            prefs.writeSettings(SettingsDefaultsMigration.upgrade(prefs.readSettings()))
            prefs[KEY_DEFAULTS_REVISION] = SettingsDefaultsMigration.REVISION
        }
    }

    /** One writer for both [update] and [applyDefaultRevision]: every key, every time. */
    private fun MutablePreferences.writeSettings(updated: AppSettings) {
        this[KEY_WIFI_ONLY] = updated.wifiOnly
        this[KEY_REQUIRES_CHARGING] = updated.requiresCharging
        this[KEY_ALLOW_MOBILE_DATA] = updated.allowMobileData
        this[KEY_ROOT_BG_DOWNLOADS] = updated.rootBackgroundDownloadsEnabled
        this[KEY_ROOT_SILENT_INSTALL] = updated.rootSilentInstallEnabled
        this[KEY_UPDATE_INTERVAL_HOURS] = updated.updateIntervalHours
        this[KEY_QUEUE_MODE] = updated.queueMode.name
        this[KEY_THEME_MODE] = updated.themeMode.name
        this[KEY_LANGUAGE] = updated.language.name
        this[KEY_DYNAMIC_COLOR] = updated.dynamicColorEnabled
        this[KEY_GITHUB_ENABLED] = updated.githubEnabled
        this[KEY_READY_NOTIFICATIONS] = updated.readyNotificationsEnabled
        this[KEY_ERROR_NOTIFICATIONS] = updated.errorNotificationsEnabled
        this[KEY_CHECK_SUMMARY_NOTIFICATIONS] = updated.checkSummaryNotificationsEnabled
        this[KEY_QUIET_HOURS_ENABLED] = updated.quietHoursEnabled
        this[KEY_QUIET_HOURS_START] = updated.quietHoursStart
        this[KEY_QUIET_HOURS_END] = updated.quietHoursEnd
        this[KEY_RESPECT_BATTERY_SAVER] = updated.respectBatterySaver
        this[KEY_SELF_UPDATE_ENABLED] = updated.selfUpdateEnabled
        this[KEY_SILENT_UPDATES] = updated.silentUpdatesEnabled
        this[KEY_AUTO_DOWNLOAD_UPDATES] = updated.autoDownloadUpdates
        this[KEY_AUTO_INSTALL_UPDATES] = updated.autoInstallUpdates
        this[KEY_AUTO_INSTALL_NEW_APPS] = updated.autoInstallNewApps
        this[KEY_ARTIFACT_RETENTION_DAYS] = updated.artifactRetentionDays
        this[KEY_ARTIFACT_STORAGE_LIMIT_MB] = updated.artifactStorageLimitMb
    }

    /**
     * Latest value, for the synchronous callers (workers, Activity startup) that cannot suspend.
     *
     * Reading DataStore blocks on file I/O, so the value is cached process-wide and kept warm by
     * [warmUp]; only a genuinely cold first call falls back to blocking.
     */
    fun currentSettings(): AppSettings = cached ?: runBlocking { settings.first() }.also { cached = it }

    /**
     * Starts mirroring settings into the synchronous cache. Called once at process start so later
     * [currentSettings] calls never touch disk on the main thread.
     */
    fun warmUp(scope: CoroutineScope) {
        scope.launch {
            settings.collect { cached = it }
        }
    }

}

@Volatile
private var cached: AppSettings? = null
