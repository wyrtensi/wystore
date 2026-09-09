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
    val iconUrl: String? = null,
    /**
     * The name of a section Wy Store assembles itself, translated with the interface.
     *
     * Sections read from the source keep [title], which is whatever the catalogue publishes and is
     * not the app's to translate. Wy Store's own sections had their names written into the code in
     * Russian, so an English interface showed a rail of Russian tiles.
     */
    val titleRes: Int? = null
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
    val signingDigests: Set<String>,
    /**
     * Who Android holds responsible for this app's updates, or null when nobody was given the job
     * (sideloaded by hand, pushed over adb). The coarse [source] tells Play from everything else;
     * this is the name the user can actually be shown.
     */
    val installerPackageName: String? = null
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
    /** Off by default: an update that waits for a charger is an update that never arrives. */
    val requiresCharging: Boolean = false,
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
    /** Update an app Wy Store installed without stopping on Android's dialog. */
    val silentUpdatesEnabled: Boolean = true,
    /** Fetch an update as soon as a check finds it, instead of waiting to be asked. */
    val autoDownloadUpdates: Boolean = true,
    /**
     * Whether a check looks at apps taken out of auto-updates at all.
     *
     * Off, and the switch means what it says: the app is not checked, nothing about it appears in
     * the queue, and it costs no request. On, the update is found and shown so it can be started by
     * hand - it is still never downloaded on its own, that is decided separately.
     */
    val showExcludedUpdates: Boolean = false,
    /** Hand a downloaded update straight to the installer instead of waiting for a tap. */
    val autoInstallUpdates: Boolean = true,
    /** The same for an app being installed for the first time. */
    val autoInstallNewApps: Boolean = true,
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

/** Why a check could not report an update for an app. */
enum class CheckProblemReason {
    /** The source did not answer for it, or answered with something unreadable. */
    UNREACHABLE,

    /**
     * The source signs the app with a different certificate than the phone installed it under.
     * Android refuses to update over that whatever is downloaded, so there is nothing to offer -
     * which used to be indistinguishable from "this app is up to date".
     */
    SIGNATURE_CHANGED
}

data class UpdateCheckProblem(
    val packageName: String,
    val label: String,
    val reason: CheckProblemReason
)

data class UpdateCheckSummary(
    val finishedAt: Long,
    val detail: String,
    val checked: Int,
    val total: Int,
    val updates: Int,
    val problems: Int,
    val manual: Boolean,
    /**
     * Which apps, and why.
     *
     * The card used to show a bare count. "2 problems" is not something anyone can act on: it does
     * not say which apps, and it does not say whether they are unreachable right now or cannot be
     * updated from here at all.
     */
    val problemApps: List<UpdateCheckProblem> = emptyList()
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
