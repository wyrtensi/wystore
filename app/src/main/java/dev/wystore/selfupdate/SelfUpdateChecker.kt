package dev.wystore.selfupdate

import android.content.Context
import dev.wystore.BuildConfig
import dev.wystore.data.GitHubRelease
import dev.wystore.data.GitHubReleasePolicy
import dev.wystore.data.GitHubReleaseSource
import dev.wystore.data.GitHubRepository
import dev.wystore.data.ManagedSource
import dev.wystore.updates.QueueRepository

/** Where a self-update check ended up. */
sealed interface SelfUpdateStatus {
    data object Idle : SelfUpdateStatus
    data object Checking : SelfUpdateStatus
    data object UpToDate : SelfUpdateStatus
    data class Available(val versionName: String, val release: GitHubRelease) : SelfUpdateStatus
    data class Failed(val reason: String) : SelfUpdateStatus
}

/**
 * Keeps Wy Store itself up to date from its own public repository.
 *
 * The update runs through the ordinary queue rather than a private code path: the same download,
 * the same signature check against the installed copy, the same install confirmation. An APK
 * signed with a different key is therefore refused here exactly as it would be for any other app,
 * which is the point — a store that updated itself without checking would be the weakest link in
 * everything else it does.
 */
class SelfUpdateChecker(
    private val context: Context,
    private val releaseSource: GitHubReleaseSource = GitHubReleaseSource(context),
    private val queueRepository: QueueRepository = QueueRepository.getInstance(context)
) {

    val currentVersionName: String get() = BuildConfig.VERSION_NAME
    val currentVersionCode: Long get() = BuildConfig.VERSION_CODE.toLong()

    suspend fun check(): SelfUpdateStatus = runCatching {
        val releases = releaseSource.releases(REPOSITORY)
        val release = GitHubReleasePolicy.selectRelease(releases, assetPattern = null)
            ?: return@runCatching SelfUpdateStatus.UpToDate
        if (!SelfUpdateVersion.isNewer(release.tagName, currentVersionName)) {
            SelfUpdateStatus.UpToDate
        } else {
            SelfUpdateStatus.Available(release.tagName.removePrefix("v"), release)
        }
    }.getOrElse { error ->
        SelfUpdateStatus.Failed(error.message.orEmpty())
    }

    /**
     * Puts the release in the ordinary download queue. The install itself replaces this process,
     * so nothing here can run afterwards; everything that matters is already persisted.
     */
    suspend fun enqueue(release: GitHubRelease) {
        queueRepository.enqueueAvailableUpdate(
            packageName = context.packageName,
            label = APP_LABEL,
            versionName = release.tagName.removePrefix("v"),
            // Placeholder: verification replaces it with the version read out of the APK.
            versionCode = release.id,
            source = ManagedSource.GITHUB,
            githubRepository = REPOSITORY,
            githubReleaseId = release.id
        )
    }

    companion object {
        const val APP_LABEL = "Wy Store"
        val REPOSITORY = GitHubRepository(owner = "wyrtensi", name = "wystore")
        const val PROJECT_URL = "https://github.com/wyrtensi/wystore"
        const val DISCLAIMER_URL = "https://github.com/wyrtensi/wystore/blob/main/DISCLAIMER.md"
    }
}
