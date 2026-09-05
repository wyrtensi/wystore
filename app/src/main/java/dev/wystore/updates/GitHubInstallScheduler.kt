package dev.wystore.updates

import android.content.Context
import dev.wystore.background.TransferDispatcher
import dev.wystore.data.GitHubAsset
import dev.wystore.data.GitHubRepository
import dev.wystore.data.ManagedSource
import dev.wystore.data.StoreSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object GitHubInstallScheduler {
    const val KEY_NAME = "asset_name"
    const val KEY_URL = "asset_url"
    const val KEY_SIZE = "asset_size"
    const val KEY_DIGEST = "asset_digest"
    const val KEY_REPOSITORY_OWNER = "repository_owner"
    const val KEY_REPOSITORY_NAME = "repository_name"
    const val KEY_AUTOMATIC = "automatic"
    const val KEY_RELEASE_ID = "release_id"
    const val ASSET_TAG_PREFIX = "wy_store_github_asset:"

    /**
     * Placeholder package name for a release asset.
     *
     * A GitHub queue row exists before the APK is downloaded, and a release file name is not a
     * package name. The row previously stored `asset.name` minus `.apk`, which the download worker
     * then asserted against the real package inside the archive, so every GitHub install failed
     * verification. The real name is adopted once verification reads it.
     */
    fun placeholderPackageName(repository: GitHubRepository): String =
        PLACEHOLDER_PREFIX + "${repository.owner}.${repository.name}".lowercase()
            .replace(Regex("[^a-z0-9]+"), ".")
            .trim('.')

    fun isPlaceholder(packageName: String): Boolean = packageName.startsWith(PLACEHOLDER_PREFIX)

    private const val PLACEHOLDER_PREFIX = "pending.github."

    fun enqueue(
        context: Context,
        asset: GitHubAsset,
        repository: GitHubRepository,
        releaseId: Long? = null,
        automatic: Boolean = false,
        settings: StoreSettings? = null
    ) {
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            val queueRepo = QueueRepository.getInstance(appContext)
            val queueEntity = queueRepo.enqueueAvailableUpdate(
                packageName = placeholderPackageName(repository),
                label = asset.name,
                versionName = "",
                versionCode = releaseId ?: asset.id,
                source = ManagedSource.GITHUB,
                priority = if (automatic) 0 else 10,
                githubRepository = repository,
                githubReleaseId = releaseId ?: asset.id
            )
            TransferDispatcher.dispatch(appContext, queueEntity.id)
        }
    }
}
