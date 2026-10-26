package dev.wystore.updates

import android.content.Context
import dev.wystore.background.TransferDispatcher
import dev.wystore.data.GitHubAsset
import dev.wystore.data.GitHubCatalog
import dev.wystore.data.GitHubRepository
import dev.wystore.data.ManagedSource
import dev.wystore.data.StoreSettings
import dev.wystore.updates.model.QueueState
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

    fun queuePackageName(repository: GitHubRepository, expectedPackageName: String? = null): String =
        expectedPackageName ?: placeholderPackageName(repository)

    fun mustRefetchOwnershipApk(state: String, previous: GitHubAsset?, selected: GitHubAsset): Boolean =
        state in setOf(QueueState.READY_TO_INSTALL.name, QueueState.AWAITING_UNKNOWN_SOURCES_PERMISSION.name) &&
            previous != selected

    private const val PLACEHOLDER_PREFIX = "pending.github."

    private val RESTARTABLE_STATES = setOf(
        dev.wystore.updates.model.QueueState.FAILED.name,
        dev.wystore.updates.model.QueueState.CANCELED.name,
        dev.wystore.updates.model.QueueState.SKIPPED.name
    )

    fun enqueue(
        context: Context,
        asset: GitHubAsset,
        repository: GitHubRepository,
        releaseId: Long? = null,
        automatic: Boolean = false,
        settings: StoreSettings? = null,
        requiresUpdateOwnership: Boolean = false,
        expectedPackageName: String? = null
    ) {
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            enqueuePrepared(appContext, asset, repository, releaseId, automatic, settings, requiresUpdateOwnership, expectedPackageName)
        }
    }

    /** Keeps the pending handover on disk until its replacement is durably queued and dispatched. */
    suspend fun enqueuePrepared(
        context: Context,
        asset: GitHubAsset,
        repository: GitHubRepository,
        releaseId: Long? = null,
        automatic: Boolean = false,
        settings: StoreSettings? = null,
        requiresUpdateOwnership: Boolean = false,
        expectedPackageName: String? = null
    ): String? {
        val appContext = context.applicationContext
        val queueRepo = QueueRepository.getInstance(appContext)
        val queueEntity = queueRepo.enqueueAvailableUpdate(
            packageName = queuePackageName(repository, expectedPackageName),
            // The name of the app, not the name of the file inside the release. The queue and
            // the "ready to install" list were showing rows called
            // "ByeByeDPI-v1.7.8-arm64-v8a-release.apk".
            label = GitHubCatalog.find(repository.displayName)?.title ?: repository.name,
            versionName = "",
            versionCode = releaseId ?: asset.id,
            source = ManagedSource.GITHUB,
            priority = if (automatic) 0 else 10,
            githubRepository = repository,
            // Only a real release id, never the asset's. They come from different numbering,
            // so recording one in place of the other guaranteed that no later check could ever
            // match it and the same release was offered again forever.
            githubReleaseId = releaseId
        )
        // The same release asked for again after it stopped finds its old row, still FAILED or
        // CANCELED. A transfer cannot start from there, so the tap did nothing; a person asking
        // again is the retry.
        // The file that was tapped, not whichever one the worker would prefer.
        val assetChoice = GitHubAssetChoice(appContext)
        val previousExact = assetChoice.exact(queueEntity.id)
        if (requiresUpdateOwnership && mustRefetchOwnershipApk(queueEntity.state, previousExact, asset)) {
            // A cached APK can be reused only when its pinned identity is the file approved now.
            // Reset before persisting the new choice so the old bytes cannot be offered as it.
            queueRepo.resetForRetry(queueEntity.id, errorCode = null, errorDetail = null)
        }
        if (GitHubAssetChoice.shouldRememberExact(requiresUpdateOwnership, queueEntity.requiresUpdateOwnership, automatic)) {
            // A new manual file choice replaces the original handover's choice. An unattended
            // check must keep the file the person already approved for this queue row.
            assetChoice.rememberExact(queueEntity.id, asset)
        } else if (!automatic) assetChoice.remember(queueEntity.id, asset.id)
        val finishedHandover = requiresUpdateOwnership && queueEntity.state in setOf(
            QueueState.INSTALLED.name,
            QueueState.OFFER_NEXT.name
        )
        if ((!automatic && queueEntity.state in RESTARTABLE_STATES) || finishedHandover) {
            queueRepo.resetForRetry(queueEntity.id, errorCode = null, errorDetail = null)
        }
        if (requiresUpdateOwnership) {
            queueRepo.requireUpdateOwnership(queueEntity.id)
            queueRepo.endHandoff(queueEntity.id)
        }
        if (requiresUpdateOwnership) PreparedInstallDispatcher.dispatch(appContext, queueEntity.id)
        else TransferDispatcher.dispatch(appContext, queueEntity.id)
        return queueEntity.id
    }
}
