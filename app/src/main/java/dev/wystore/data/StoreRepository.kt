package dev.wystore.data

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dev.wystore.settings.SettingsRepository
import dev.wystore.settings.toAppSettings
import dev.wystore.settings.toStoreSettings
import kotlinx.coroutines.runBlocking

class StoreRepository(private val context: Context) {
    private val preferences = context.getSharedPreferences("wy_store", Context.MODE_PRIVATE)
    private val settingsRepository = SettingsRepository(context)
    private val gson = Gson()

    fun managedApps(): List<ManagedApp> {
        val json = preferences.getString("managed_apps", "[]") ?: "[]"
        return runCatching {
            gson.fromJson<List<ManagedApp>>(json, object : TypeToken<List<ManagedApp>>() {}.type)
        }.getOrDefault(emptyList())
    }

    fun saveManaged(app: ManagedApp) {
        val updated = managedApps().filterNot { it.packageName == app.packageName } + app
        // commit(), not apply(): this is called from a broadcast receiver right after an install,
        // and the process can be torn down before an asynchronous write reaches disk — which loses
        // the registration that makes the app eligible for updates.
        preferences.edit().putString("managed_apps", gson.toJson(updated)).commit()
    }

    fun removeManaged(packageName: String) {
        preferences.edit().putString("managed_apps", gson.toJson(managedApps().filterNot { it.packageName == packageName })).apply()
    }

    /**
     * Drops managed apps that are no longer on the device.
     *
     * Pruning requires positive evidence of absence. [packageNames] comes from
     * [installedApps], whose `getInstalledPackages` result the platform caches per process, and the
     * app cannot refresh that cache on install because manifest receivers for `PACKAGE_ADDED` do
     * not fire on API 26+. An app installed moments ago is therefore missing from that list, and
     * pruning on it alone deleted the registration that had just been written — which is why an app
     * installed through Wy Store never received updates.
     */
    fun retainManagedInstalled(packageNames: Set<String>): List<ManagedApp> {
        val retained = managedApps().filter { it.packageName in packageNames || isInstalled(it.packageName) }
        preferences.edit().putString("managed_apps", gson.toJson(retained)).apply()
        return retained
    }

    /** Direct query, which is not served from the cached installed-package list. */
    fun isInstalled(packageName: String): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(packageName, 0)
        }
        true
    }.getOrDefault(false)

    /**
     * Settings come from [SettingsRepository] (DataStore) only.
     *
     * This class used to keep a second copy in SharedPreferences under different key names, so the
     * UI and the background workers could read different values for the same switch. DataStore is
     * now the single source of truth and the legacy keys are imported once by
     * [dev.wystore.settings.settingsDataStore]'s SharedPreferences migration.
     */
    fun settings(): StoreSettings = settingsRepository.currentSettings().toStoreSettings()

    fun saveSettings(settings: StoreSettings) {
        val next = settings.copy(updateIntervalHours = settings.updateIntervalHours.coerceIn(1, 24))
        runBlocking { settingsRepository.update { next.toAppSettings() } }
    }

    fun ruStoreCompatibility(): RuStoreCompatibility = RuStoreCompatibility(
        apiVersionCode = if (preferences.contains("rustore_api_version_code")) {
            preferences.getLong("rustore_api_version_code", RuStoreApiCompatibilityPolicy.DEFAULT_VERSION_CODE)
        } else {
            RuStoreApiCompatibilityPolicy.migrateLegacyCode(
                preferences.getLong("rustore_version_code", RuStoreApiCompatibilityPolicy.DEFAULT_VERSION_CODE)
            )
        },
        verifiedVersionName = preferences.getString("rustore_verified_version_name", null),
        verifiedVersionCode = preferences.getLong("rustore_verified_version_code", 0L).takeIf { it > 0L },
        verifiedAt = preferences.getLong("rustore_verified_at", 0L).takeIf { it > 0L }
    )

    fun saveRuStoreCompatibility(compatibility: RuStoreCompatibility) {
        require(compatibility.apiVersionCode > 0L) { "API-код должен быть положительным" }
        preferences.edit()
            .putLong("rustore_api_version_code", compatibility.apiVersionCode)
            .putLong("rustore_version_code", compatibility.apiVersionCode)
            .putString("rustore_verified_version_name", compatibility.verifiedVersionName)
            .putLong("rustore_verified_version_code", compatibility.verifiedVersionCode ?: 0L)
            .putLong("rustore_verified_at", compatibility.verifiedAt ?: 0L)
            .apply()
    }

    fun hasStoredRuStoreApiVersionCode(versionCode: Long): Boolean =
        preferences.contains("rustore_api_version_code") &&
            preferences.getLong("rustore_api_version_code", 0L) == versionCode

    fun saveRuStoreApiVersionCode(versionCode: Long) {
        require(versionCode > 0L) { "API-код должен быть положительным" }
        preferences.edit()
            .putLong("rustore_api_version_code", versionCode)
            .putLong("rustore_version_code", versionCode)
            .apply()
    }

    fun saveVerifiedRuStoreApk(versionName: String?, versionCode: Long, verifiedAt: Long) {
        require(versionCode > 0L) { "VersionCode APK должен быть положительным" }
        preferences.edit()
            .putString("rustore_verified_version_name", versionName)
            .putLong("rustore_verified_version_code", versionCode)
            .putLong("rustore_verified_at", verifiedAt)
            .apply()
    }

    fun lastUpdateCheck(): UpdateCheckSummary? {
        val finishedAt = preferences.getLong("last_update_check_at", 0L)
        if (finishedAt <= 0L) return null
        return UpdateCheckSummary(
            finishedAt = finishedAt,
            detail = preferences.getString("last_update_check_detail", null) ?: "Проверка завершена",
            checked = preferences.getInt("last_update_check_checked", 0),
            total = preferences.getInt("last_update_check_total", 0),
            updates = preferences.getInt("last_update_check_updates", 0),
            problems = preferences.getInt("last_update_check_problems", 0),
            manual = preferences.getBoolean("last_update_check_manual", false)
        )
    }

    fun saveLastUpdateCheck(summary: UpdateCheckSummary) {
        preferences.edit()
            .putLong("last_update_check_at", summary.finishedAt)
            .putString("last_update_check_detail", summary.detail)
            .putInt("last_update_check_checked", summary.checked)
            .putInt("last_update_check_total", summary.total)
            .putInt("last_update_check_updates", summary.updates)
            .putInt("last_update_check_problems", summary.problems)
            .putBoolean("last_update_check_manual", summary.manual)
            .apply()
    }

    fun githubRepositories(): List<GitHubRepository> {
        val json = preferences.getString("github_repositories", "[]") ?: "[]"
        return runCatching {
            gson.fromJson<List<GitHubRepository>>(json, object : TypeToken<List<GitHubRepository>>() {}.type)
        }.getOrDefault(emptyList())
    }

    fun saveGithubRepository(repository: GitHubRepository) {
        val updated = githubRepositories().filterNot { it == repository } + repository
        preferences.edit().putString("github_repositories", gson.toJson(updated)).apply()
    }

    fun removeGithubRepository(repository: GitHubRepository) {
        preferences.edit().putString("github_repositories", gson.toJson(githubRepositories().filterNot { it == repository })).apply()
    }

    @Suppress("DEPRECATION")
    /**
     * Every non-system app on the device.
     *
     * This is the most expensive read in the app: a full `getInstalledPackages` with signing
     * certificates, then a label lookup and a SHA-256 of every certificate, per package. It runs
     * on every resume and after every install, so the result is held until something actually
     * changes the set of installed packages — see [invalidateInstalledApps].
     */
    fun installedApps(): List<InstalledApp> {
        installedCache?.let { return it }
        return readInstalledApps().also { installedCache = it }
    }

    private fun readInstalledApps(): List<InstalledApp> {
        val packages = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
            )
        } else {
            context.packageManager.getInstalledPackages(SigningFlags.forSdk(Build.VERSION.SDK_INT))
        }
        return packages.asSequence()
            .filter { it.applicationInfo?.flags?.and(android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 }
            .filterNot { it.packageName == context.packageName }
            .map { info ->
                InstalledApp(
                    packageName = info.packageName,
                    label = info.applicationInfo?.loadLabel(context.packageManager)?.toString().orEmpty(),
                    versionName = info.versionName.orEmpty(),
                    versionCode = info.versionCodeCompat(),
                    lastUpdateTime = info.lastUpdateTime,
                    source = sourceFor(info.packageName),
                    signingDigests = SigningVerifier.installedDigests(info)
                )
            }.sortedBy { it.label.lowercase() }.toList()
    }

    fun createBackup(): WyStoreBackup = WyStoreBackup(
        version = 3,
        exportedAt = System.currentTimeMillis(),
        settings = settings(),
        ruStoreCompatibility = ruStoreCompatibility(),
        managedApps = managedApps(),
        githubRepositories = githubRepositories()
    )

    fun exportBackupJson(): String =
        com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(createBackup())

    fun restoreBackup(backup: WyStoreBackup, merge: Boolean = false): BackupRestoreSummary {
        saveSettings(backup.settings)
        if (backup.ruStoreCompatibility.apiVersionCode > 0L) {
            saveRuStoreCompatibility(
                backup.ruStoreCompatibility.copy(
                    apiVersionCode = RuStoreApiCompatibilityPolicy.codeFromBackup(
                        backup.version,
                        backup.ruStoreCompatibility.apiVersionCode
                    )
                )
            )
        }

        val currentRepos = if (merge) githubRepositories() else emptyList()
        val mergedRepos = (currentRepos + backup.githubRepositories).distinctBy { it.url }
        preferences.edit().putString("github_repositories", gson.toJson(mergedRepos)).apply()

        val currentManaged = if (merge) managedApps().associateBy { it.packageName }.toMutableMap() else mutableMapOf()
        backup.managedApps.forEach { app ->
            currentManaged[app.packageName] = app
        }
        preferences.edit().putString("managed_apps", gson.toJson(currentManaged.values.toList())).apply()

        return BackupRestoreSummary(
            managedAppsCount = currentManaged.size,
            githubRepositoriesCount = mergedRepos.size,
            message = "Восстановлено: ${currentManaged.size} приложений, ${mergedRepos.size} репозиториев"
        )
    }

    fun restoreBackupJson(json: String, merge: Boolean = false): BackupRestoreSummary {
        val backup = runCatching { gson.fromJson(json, WyStoreBackup::class.java) }.getOrNull()
            ?: throw IllegalArgumentException("Некорректный формат JSON резервной копии")
        return restoreBackup(backup, merge)
    }

    private fun sourceFor(packageName: String): InstallSource {
        val installer = if (Build.VERSION.SDK_INT >= 30) {
            runCatching { context.packageManager.getInstallSourceInfo(packageName).installingPackageName }.getOrNull()
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getInstallerPackageName(packageName)
        }
        return if (installer == "com.android.vending") InstallSource.GOOGLE_PLAY else InstallSource.OTHER
    }
}

@Suppress("DEPRECATION")
private fun PackageInfo.versionCodeCompat(): Long = if (Build.VERSION.SDK_INT >= 28) longVersionCode else versionCode.toLong()

/**
 * Cached result of [StoreRepository.installedApps], shared by every repository instance in the
 * process because they all describe the same device.
 *
 * Invalidated rather than expired: the package-change receiver and the install flow know exactly
 * when the list stops being true, and a time-based cache would either be stale right after an
 * install or useless on a resume.
 */
@Volatile
private var installedCache: List<InstalledApp>? = null

/** Drops the cached package list. Call after anything that installs, updates or removes an app. */
fun invalidateInstalledApps() {
    installedCache = null
}
