package dev.wystore.data

import dev.wystore.R
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

    fun managedApps(): List<ManagedApp> = synchronized(MANAGED_LOCK) { readManagedApps() }

    /** Keep the revision through an uninstall/reinstall gap so obsolete requests remain obsolete. */
    fun sourceRevision(packageName: String): Long = synchronized(MANAGED_LOCK) {
        maxOf(preferences.getLong("source_revision:$packageName", 0),
            readManagedApps().firstOrNull { it.packageName == packageName }?.sourceRevision ?: 0)
    }

    fun advanceSourceRevision(packageName: String): Long = synchronized(MANAGED_LOCK) {
        Math.addExact(sourceRevision(packageName), 1).also { revision ->
            check(preferences.edit().putLong("source_revision:$packageName", revision).commit())
        }
    }

    internal fun <T> withManagedAppsLock(block: () -> T): T = synchronized(MANAGED_LOCK, block)

    private fun readManagedApps(): List<ManagedApp> {
        val json = preferences.getString("managed_apps", "[]") ?: "[]"
        val decoded = runCatching {
            gson.fromJson<List<ManagedApp>>(json, object : TypeToken<List<ManagedApp>>() {}.type)
        }.getOrDefault(emptyList())
        val ordinary = decoded.filterNot { it.packageName == context.packageName }
        // Self-updates share the install queue, but never the adopted-app registry. Repair 0.6.0
        // records on read so workers and restored backups cannot keep checking us twice.
        if (ordinary.size != decoded.size) {
            check(preferences.edit().putString("managed_apps", gson.toJson(ordinary)).commit())
        }
        return ordinary
    }

    fun saveManaged(app: ManagedApp, clearGooglePlayUpdate: Boolean = false) {
        updateManaged(app.packageName, clearGooglePlayUpdate) { app }
    }

    /** Registration, observation and UI edits share one lock across repository instances. */
    fun updateManaged(
        packageName: String,
        clearGooglePlayUpdate: Boolean = false,
        transform: (ManagedApp?) -> ManagedApp
    ): ManagedApp = synchronized(MANAGED_LOCK) {
        require(packageName != context.packageName) { "Wy Store uses its dedicated self-updater" }
        val current = readManagedApps()
        val previous = current.firstOrNull { it.packageName == packageName }
        val replacement = SourceRequestPolicy.preserveSelection(previous, transform(previous))
        require(replacement.packageName == packageName)
        replacement.githubRepository?.takeIf { it != previous?.githubRepository }?.let(ReservedGitHubRepository::requireAllowed)
        replacement.knownGithubRepository?.takeIf { it != previous?.knownGithubRepository }?.let(ReservedGitHubRepository::requireAllowed)
        val merged = replacement.copy(sourceRevision = maxOf(replacement.sourceRevision, sourceRevision(packageName)),
            googlePlayUpdateDetected = !clearGooglePlayUpdate &&
            (replacement.googlePlayUpdateDetected || previous?.googlePlayUpdateDetected == true))
        writeManagedApps(current.filterNot { it.packageName == packageName } + merged)
        merged
    }

    fun observeGooglePlayUpdates(installed: List<InstalledApp>): List<ManagedApp> = synchronized(MANAGED_LOCK) {
        val current = readManagedApps()
        val managedNames = current.filterNot { it.googlePlayUpdateDetected }.mapTo(HashSet()) { it.packageName }
        // A scan may predate a verified ownership handover. Confirm attribution under the same
        // lock as the handover reset so that stale scan cannot restore the previous pause.
        val playPackages = installed.asSequence()
            .filter { it.packageName in managedNames && it.installerPackageName == "com.android.vending" }
            .mapNotNull { installedAppFresh(it.packageName) }
            .filter { it.installerPackageName == "com.android.vending" }
            .mapTo(HashSet()) { it.packageName }
        val observed = current.map { app ->
            if (app.packageName in playPackages) app.copy(googlePlayUpdateDetected = true) else app
        }
        if (observed != current) writeManagedApps(observed)
        observed
    }

    private fun writeManagedApps(updated: List<ManagedApp>) {
        // commit(), not apply(): this is called from a broadcast receiver right after an install,
        // and the process can be torn down before an asynchronous write reaches disk — which loses
        // the registration that makes the app eligible for updates.
        val old = readManagedApps().associateBy { it.packageName }
        val normalized = updated.filterNot { it.packageName == context.packageName }.map { app ->
            val previous = old[app.packageName]
            val epoch = maxOf(preferences.getLong("source_revision:${app.packageName}", 0), previous?.sourceRevision ?: 0)
            if (app.sourceRevision >= epoch) app else app.copy(sourceRevision =
                if (previous != null && previous.source == app.source && previous.githubRepository == app.githubRepository) epoch
                else Math.addExact(epoch, 1))
        }
        val editor = preferences.edit().putString("managed_apps", gson.toJson(normalized))
        normalized.forEach { editor.putLong("source_revision:${it.packageName}", it.sourceRevision) }
        check(editor.commit()) {
            "Could not persist managed apps"
        }
    }

    fun removeManaged(packageName: String) = synchronized(MANAGED_LOCK) {
        writeManagedApps(readManagedApps().filterNot { it.packageName == packageName })
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
    fun retainManagedInstalled(packageNames: Set<String>): List<ManagedApp> = synchronized(MANAGED_LOCK) {
        val current = readManagedApps()
        val retained = current.filter { it.packageName in packageNames || isInstalled(it.packageName) }
        if (retained != current) writeManagedApps(retained)
        retained
    }

    /**
     * Direct query, which is not served from the cached installed-package list.
     *
     * Frozen apps count as installed: the plain lookup missed them, and the pruning above deleted
     * the record of every app the user froze in Shelter, so unfreezing it brought back an app Wy
     * Store no longer looked after.
     */
    fun isInstalled(packageName: String): Boolean =
        InstalledPackageLookup.isInstalled(context.packageManager, packageName)

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
        require(compatibility.apiVersionCode > 0L) { "API version code must be positive" }
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
        require(versionCode > 0L) { "API version code must be positive" }
        preferences.edit()
            .putLong("rustore_api_version_code", versionCode)
            .putLong("rustore_version_code", versionCode)
            .apply()
    }

    fun saveVerifiedRuStoreApk(versionName: String?, versionCode: Long, verifiedAt: Long) {
        require(versionCode > 0L) { "APK version code must be positive" }
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
            checked = preferences.getInt("last_update_check_checked", 0),
            total = preferences.getInt("last_update_check_total", 0),
            updates = preferences.getInt("last_update_check_updates", 0),
            problems = preferences.getInt("last_update_check_problems", 0),
            manual = preferences.getBoolean("last_update_check_manual", false),
            problemApps = runCatching {
                val json = preferences.getString("last_update_check_problem_apps", null)
                    ?: return@runCatching emptyList()
                gson.fromJson<List<UpdateCheckProblem>>(
                    json,
                    object : TypeToken<List<UpdateCheckProblem>>() {}.type
                ) ?: emptyList()
            }.getOrDefault(emptyList())
        )
    }

    fun saveLastUpdateCheck(summary: UpdateCheckSummary) {
        preferences.edit()
            .putLong("last_update_check_at", summary.finishedAt)
            .remove("last_update_check_detail")
            .putInt("last_update_check_checked", summary.checked)
            .putInt("last_update_check_total", summary.total)
            .putInt("last_update_check_updates", summary.updates)
            .putInt("last_update_check_problems", summary.problems)
            .putBoolean("last_update_check_manual", summary.manual)
            .putString("last_update_check_problem_apps", gson.toJson(summary.problemApps))
            .apply()
    }

    fun githubRepositories(): List<GitHubRepository> {
        val json = preferences.getString("github_repositories", "[]") ?: "[]"
        return runCatching {
            gson.fromJson<List<GitHubRepository>>(json, object : TypeToken<List<GitHubRepository>>() {}.type)
        }.getOrDefault(emptyList()).filter { ReservedGitHubRepository.error(it) == null }
    }

    fun saveGithubRepository(repository: GitHubRepository) {
        ReservedGitHubRepository.requireAllowed(repository)
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
        // Nothing above this is prepared for it to throw, and everything goes through it: the
        // update check, the download worker, the installer and every return to a screen. A phone
        // where this read fails once - a package too large for one binder transaction, a vendor
        // build that answers differently - would take down whatever happened to be running, and
        // leave nothing behind to say why, because the store's own recording lives further in.
        // The failure is written down instead, and deliberately not cached: the next caller asks
        // again rather than inheriting an empty device forever.
        return runCatching { readInstalledApps() }
            .onSuccess { installedCache = it }
            .getOrElse { error ->
                runCatching {
                    EventLog(context).record(
                        packageName = context.packageName,
                        code = "INSTALLED_APPS_UNREADABLE",
                        detail = "${error.javaClass.simpleName}: ${error.message}"
                    )
                }
                emptyList()
            }
    }

    /**
     * One app, asked for by name.
     *
     * The verification paths need exactly one package, and they used to pick it out of the full
     * list. That list is now allowed to come back empty when the bulk read fails, and an empty
     * list is indistinguishable from "not installed" - which would drop the comparison of the
     * downloaded certificate against the installed one, quietly, in the one place it matters. A
     * single-package read cannot be confused that way: absent throws NameNotFound, and anything
     * else propagates instead of pretending the app is new.
     */
    fun installedApp(packageName: String): InstalledApp? {
        installedCache?.let { cached ->
            cached.firstOrNull { it.packageName == packageName }?.let { return it }
        }
        // Frozen apps included - see InstalledPackageLookup.
        val info = InstalledPackageLookup.packageInfo(
            context.packageManager,
            packageName,
            SigningFlags.forSdk(Build.VERSION.SDK_INT)
        ) ?: return null
        return info.toInstalledApp().copy(
            frozen = !InstalledPackageLookup.visibleWithoutFlag(context.packageManager, packageName)
        )
    }

    /** Update/download decisions must not reuse the list captured before another store updated it. */
    fun installedAppFresh(packageName: String): InstalledApp? {
        val info = InstalledPackageLookup.packageInfoStrict(
            context.packageManager, packageName, SigningFlags.forSdk(Build.VERSION.SDK_INT)
        ) ?: return null
        return info.toInstalledApp(readInstallSourceOf(packageName))
    }

    private fun PackageInfo.toInstalledApp(
        installSource: PackageInstallSource = installSourceOf(packageName)
    ): InstalledApp {
        return InstalledApp(
            packageName = packageName,
            label = applicationInfo?.loadLabel(context.packageManager)?.toString().orEmpty(),
            versionName = versionName.orEmpty(),
            versionCode = versionCodeCompat(),
            lastUpdateTime = lastUpdateTime,
            source = InstalledAppOwnership.source(installSource.responsiblePackageName, context.packageName),
            signingDigests = SigningVerifier.installedDigests(this),
            installerPackageName = installSource.installerPackageName,
            preinstalled = InstallerNames.isPreinstalled(installSource.installerPackageName, applicationInfo?.sourceDir),
            updateOwnerPackageName = installSource.updateOwnerPackageName
        )
    }

    private fun readInstalledApps(): List<InstalledApp> {
        // Frozen apps included - see InstalledPackageLookup. A frozen app is one only this wider
        // read finds, which the plain list of names tells apart cheaply.
        val seeFrozen = InstalledPackageLookup.SEE_FROZEN
        val packages = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong() or seeFrozen.toLong())
            )
        } else {
            context.packageManager.getInstalledPackages(SigningFlags.forSdk(Build.VERSION.SDK_INT) or seeFrozen)
        }
        val visible = runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
            } else {
                context.packageManager.getInstalledPackages(0)
            }.mapTo(HashSet()) { it.packageName }
        }.getOrNull()
        return packages.asSequence()
            .filter { InstalledPackageLookup.isOnDevice(it.applicationInfo?.flags) }
            .filter { it.applicationInfo?.flags?.and(android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 }
            .filterNot { it.packageName == context.packageName }
            .map { info ->
                val installSource = installSourceOf(info.packageName)
                InstalledApp(
                    packageName = info.packageName,
                    label = info.applicationInfo?.loadLabel(context.packageManager)?.toString().orEmpty(),
                    versionName = info.versionName.orEmpty(),
                    versionCode = info.versionCodeCompat(),
                    lastUpdateTime = info.lastUpdateTime,
                    source = InstalledAppOwnership.source(installSource.responsiblePackageName, context.packageName),
                    signingDigests = SigningVerifier.installedDigests(info),
                    installerPackageName = installSource.installerPackageName,
                    preinstalled = InstallerNames.isPreinstalled(installSource.installerPackageName, info.applicationInfo?.sourceDir),
                    frozen = visible != null && InstalledPackageLookup.isFrozen(
                        visibleWithoutFlag = info.packageName in visible,
                        onDevice = true
                    ),
                    updateOwnerPackageName = installSource.updateOwnerPackageName
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
        ReservedGitHubRepository.requireAllowedManaged(backup.managedApps, context.packageName)
        // The interface choice describes this device, not the one the backup came from: a phone
        // restoring a TV's backup must not come up in the TV interface.
        saveSettings(backup.settings.withKnownChoices().copy(deviceType = settings().deviceType))
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
        val mergedRepos = (currentRepos + backup.githubRepositories).filter { ReservedGitHubRepository.error(it) == null }.distinctBy { it.url }
        preferences.edit().putString("github_repositories", gson.toJson(mergedRepos)).apply()

        val currentManaged = synchronized(MANAGED_LOCK) {
            val existing = readManagedApps().associateBy { it.packageName }
            val restored = if (merge) existing.toMutableMap() else mutableMapOf()
            backup.managedApps.filterNot { it.packageName == context.packageName }.forEach { app ->
                restored[app.packageName] = app.copy(googlePlayUpdateDetected =
                    app.googlePlayUpdateDetected || existing[app.packageName]?.googlePlayUpdateDetected == true)
            }
            writeManagedApps(restored.values.toList())
            restored
        }

        return BackupRestoreSummary(
            managedAppsCount = currentManaged.size,
            githubRepositoriesCount = mergedRepos.size,
            message = context.getString(
                R.string.backup_restored_summary,
                currentManaged.size,
                mergedRepos.size
            )
        )
    }

    fun restoreBackupJson(json: String, merge: Boolean = false): BackupRestoreSummary {
        val backup = runCatching { gson.fromJson(json, WyStoreBackup::class.java) }.getOrNull()
            ?: throw IllegalArgumentException(context.getString(R.string.backup_invalid_json))
        return restoreBackup(backup, merge)
    }

    /** One package-manager read keeps the recorded installer and current update owner together. */
    private fun installSourceOf(packageName: String): PackageInstallSource =
        runCatching { readInstallSourceOf(packageName) }.getOrDefault(PackageInstallSource())

    private fun readInstallSourceOf(packageName: String): PackageInstallSource = if (Build.VERSION.SDK_INT >= 30) {
        val source = context.packageManager.getInstallSourceInfo(packageName)
        PackageInstallSource(
            installerPackageName = source.installingPackageName,
            updateOwnerPackageName = if (Build.VERSION.SDK_INT >= 34) source.updateOwnerPackageName else null
        )
    } else {
        @Suppress("DEPRECATION")
        PackageInstallSource(installerPackageName = context.packageManager.getInstallerPackageName(packageName))
    }
    companion object {
        private val MANAGED_LOCK = Any()
    }
}

private data class PackageInstallSource(
    val installerPackageName: String? = null,
    val updateOwnerPackageName: String? = null
) {
    val responsiblePackageName: String?
        get() = InstalledAppOwnership.responsiblePackageName(installerPackageName, updateOwnerPackageName)
}

private fun PackageInfo.versionCodeCompat(): Long = longVersionCode

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
