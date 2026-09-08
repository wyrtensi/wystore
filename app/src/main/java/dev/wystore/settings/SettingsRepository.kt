package dev.wystore.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
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

    val settings: Flow<AppSettings> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs -> SettingsCodec.read(prefs) }

    /**
     * Saves what the caller changed, and only that.
     *
     * Nothing here ever rewrites a setting on the user's behalf: an update changes what an
     * untouched switch defaults to, never what a touched one was set to. See [SettingsCodec].
     */
    suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs ->
            val current = SettingsCodec.read(prefs)
            SettingsCodec.write(prefs, current, transform(current))
        }
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
