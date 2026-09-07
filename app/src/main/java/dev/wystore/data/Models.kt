package dev.wystore.data

import com.google.gson.annotations.SerializedName

enum class InstallSource {
    GOOGLE_PLAY,
    WY_STORE,
    OTHER
}

enum class ManagedSource {
    RUSTORE,
    GITHUB
}

data class StoreApp(
    val appId: Long,
    val packageName: String,
    val name: String,
    val publisher: String,
    val categories: List<String>,
    val shortDescription: String,
    val fullDescription: String,
    val iconUrl: String?,
    val screenshots: List<String>,
    val rating: Double?,
    val ratingCount: Int?,
    val downloadsText: String?,
    val versionName: String,
    val versionCode: Long,
    val updatedAt: String?,
    val sizeBytes: Long,
    val minAndroidVersion: String?,
    val minSdkVersion: Int? = null,
    val signatureHint: String?,
    val sourceVersionId: Long?,
    val reviews: List<StoreReview> = emptyList(),
    val changelog: AppChangelog? = null
)

/**
 * The "what's new" block from the source's app page: the notes for the version being offered.
 * Parsed from the same HTML already fetched for reviews, so it costs no extra request.
 */
data class AppChangelog(
    val versionName: String?,
    val publishedAt: String?,
    val notes: String
)

data class StoreReview(
    val author: String,
    val publishedAt: String?,
    val rating: Int?,
    val text: String
)

data class SearchPage(
    val apps: List<StoreApp>,
    val page: Int,
    val total: Int?
)

/** A RuStore catalog section, addressed by the slug in its `/catalog/<slug>` URL. */
data class StoreCategory(
    val slug: String,
    val title: String,
    val iconUrl: String? = null
)

/**
 * One page of a catalog section. [hasMore] comes from the pager the source renders, so the UI can
 * tell "this section ends here" apart from "the next page has not been fetched yet".
 */
data class CatalogPage(
    val apps: List<StoreApp>,
    val page: Int,
    val lastPage: Int?
) {
    val hasMore: Boolean get() = lastPage != null && page < lastPage
}

data class DownloadArtifact(
    val url: String,
    val sizeBytes: Long,
    val sourceHash: String?
)

data class GitHubRepository(
    val owner: String,
    val name: String
) {
    val displayName: String get() = "$owner/$name"
    val url: String get() = "https://github.com/$owner/$name"
}

data class GitHubAsset(
    val id: Long,
    val name: String,
    val sizeBytes: Long,
    val downloadUrl: String,
    val digest: String?
)

data class GitHubRelease(
    val id: Long,
    val tagName: String,
    val title: String,
    val description: String,
    val publishedAt: String?,
    val prerelease: Boolean,
    val assets: List<GitHubAsset>
)

data class InstalledApp(
    val packageName: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val lastUpdateTime: Long,
    val source: InstallSource,
    val signingDigests: Set<String>
)

data class ManagedApp(
    val packageName: String,
    val label: String,
    val pinnedDigests: Set<String>,
    val autoUpdate: Boolean = true,
    val forceWyStore: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
    val lastUpdatedAt: Long? = null,
    val source: ManagedSource? = ManagedSource.RUSTORE,
    val githubRepository: GitHubRepository? = null,
    val githubReleaseId: Long? = null
)

typealias QueueMode = dev.wystore.updates.model.QueueMode

data class StoreSettings(
    val wifiOnly: Boolean = true,
    val requiresCharging: Boolean = true,
    val allowMobileData: Boolean = false,
    val backgroundRootUpdates: Boolean = false,
    val updateIntervalHours: Long = 24,
    val queueMode: QueueMode = QueueMode.SMART_PROMPTS,
    val rootBackgroundDownloadsEnabled: Boolean = false,
    val rootSilentInstallEnabled: Boolean = false,
    val themeMode: dev.wystore.settings.ThemeMode = dev.wystore.settings.ThemeMode.SYSTEM,
    val language: dev.wystore.settings.AppLanguage = dev.wystore.settings.AppLanguage.SYSTEM,
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
    /** Hand a downloaded update straight to the installer instead of waiting for a tap. */
    val autoInstallUpdates: Boolean = false,
    /** The same for an app being installed for the first time. */
    val autoInstallNewApps: Boolean = false,
    val artifactRetentionDays: Int = 7,
    val artifactStorageLimitMb: Int = 2_048
)

data class PendingUpdate(
    val packageName: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val filePaths: List<String>,
    val signingDigests: Set<String>,
    val source: ManagedSource,
    val githubRepository: GitHubRepository? = null,
    val githubReleaseId: Long? = null,
    val downloadedAt: Long = System.currentTimeMillis()
)

data class RuStoreCompatibility(
    @SerializedName(value = "apiVersionCode", alternate = ["usedVersionCode"])
    val apiVersionCode: Long = RuStoreApiCompatibilityPolicy.DEFAULT_VERSION_CODE,
    val verifiedVersionName: String? = null,
    val verifiedVersionCode: Long? = null,
    val verifiedAt: Long? = null
)

data class UpdateCheckSummary(
    val finishedAt: Long,
    val detail: String,
    val checked: Int,
    val total: Int,
    val updates: Int,
    val problems: Int,
    val manual: Boolean
)

data class WyStoreBackup(
    val version: Int = 3,
    val exportedAt: Long = System.currentTimeMillis(),
    val settings: StoreSettings = StoreSettings(),
    val ruStoreCompatibility: RuStoreCompatibility = RuStoreCompatibility(),
    val managedApps: List<ManagedApp> = emptyList(),
    val githubRepositories: List<GitHubRepository> = emptyList()
)

data class BackupRestoreSummary(
    val managedAppsCount: Int,
    val githubRepositoriesCount: Int,
    val message: String
)

/**
 * A source did not behave as the client expects.
 *
 * [error] is what the UI renders and what [classifyThrowable] switches on; [message] stays an
 * untranslated technical detail for logs and for the bug report a user might paste.
 */
class SourceFormatException(
    val error: SourceError,
    message: String
) : IllegalStateException(message)
