package dev.wystore.updates

import android.content.Context
import androidx.core.content.edit
import dev.wystore.data.GitHubRepository
import dev.wystore.data.GitHubAsset

/** A handover the user started: this app is being removed so that one can take its place. */
data class PendingReinstall(
    val removedPackageName: String,
    val installPackageName: String,
    val label: String,
    val startedAt: Long,
    /**
     * A queue row to put back through its own source instead of enqueuing a fresh RuStore install.
     *
     * A handover started from a stopped row already knows where the file comes from - a GitHub
     * release, an asset pattern, a release id - and none of that survives being re-enqueued as a
     * package name.
     */
    val queueId: String? = null,
    val requiresUpdateOwnership: Boolean = false,
    val githubRepository: GitHubRepository? = null,
    val githubAsset: GitHubAsset? = null,
    val githubReleaseId: Long? = null,
    val sourceRevision: Long? = null
)

/**
 * Remembers a handover across the uninstall.
 *
 * Removing an app hands the screen to Android's dialog and can outlive the process, so the intent
 * to install the replacement cannot live in a composable or a ViewModel: by the time the package is
 * gone, whatever held it may be gone too. It is written before the dialog is shown and read by the
 * package-removed receiver.
 *
 * Requests are keyed by the removed package: accepting the next app must not overwrite a handover
 * whose Android uninstall dialog was cancelled or has not finished yet.
 */
class PendingReinstallStore(context: Context) {

    private val preferences =
        context.applicationContext.getSharedPreferences("wy_store_pending_reinstall", Context.MODE_PRIVATE)

    private val gson = com.google.gson.Gson()

    @Synchronized
    fun save(pending: PendingReinstall) {
        check(preferences.edit().putString(REQUEST_PREFIX + pending.removedPackageName,
            gson.toJson(pending)).commit()) { "Could not persist the pending handover" }
    }

    fun read(packageName: String? = null): PendingReinstall? =
        readAll().firstOrNull { packageName == null || it.removedPackageName == packageName }

    fun readAll(): List<PendingReinstall> {
        val requests = preferences.all.filterKeys { it.startsWith(REQUEST_PREFIX) }.values.map { value ->
            gson.fromJson(value as String, PendingReinstall::class.java).also {
                require(!it.removedPackageName.isNullOrBlank() && !it.installPackageName.isNullOrBlank())
            }
        }
        return (requests + listOfNotNull(readLegacy())).distinctBy { it.removedPackageName }.sortedBy { it.startedAt }
    }

    private fun readLegacy(): PendingReinstall? {
        val removed = preferences.getString(KEY_REMOVED, null) ?: return null
        val install = preferences.getString(KEY_INSTALL, null) ?: return null
        return PendingReinstall(
            removedPackageName = removed,
            installPackageName = install,
            label = preferences.getString(KEY_LABEL, null).orEmpty().ifBlank { install },
            startedAt = preferences.getLong(KEY_STARTED_AT, 0L),
            queueId = preferences.getString(KEY_QUEUE_ID, null),
            requiresUpdateOwnership = preferences.getBoolean(KEY_REQUIRES_UPDATE_OWNERSHIP, false),
            githubRepository = preferences.getString(KEY_GITHUB_OWNER, null)?.let { owner ->
                preferences.getString(KEY_GITHUB_NAME, null)?.let { name -> GitHubRepository(owner, name) }
            },
            githubAsset = preferences.getString(KEY_GITHUB_ASSET_NAME, null)?.let { name ->
                preferences.getString(KEY_GITHUB_ASSET_URL, null)?.let { url ->
                    GitHubAsset(
                        id = preferences.getLong(KEY_GITHUB_ASSET_ID, 0L),
                        name = name,
                        sizeBytes = preferences.getLong(KEY_GITHUB_ASSET_SIZE, 0L),
                        downloadUrl = url,
                        digest = preferences.getString(KEY_GITHUB_ASSET_DIGEST, null)
                    )
                }
            },
            githubReleaseId = if (preferences.contains(KEY_GITHUB_RELEASE_ID)) {
                preferences.getLong(KEY_GITHUB_RELEASE_ID, 0L)
            } else null
        )
    }

    @Synchronized
    fun clear(packageName: String? = null) {
        preferences.edit(commit = true) {
            if (packageName == null) clear()
            else {
                remove(REQUEST_PREFIX + packageName)
                if (readLegacy()?.removedPackageName == packageName) {
                    preferences.all.keys.filterNot { it.startsWith(REQUEST_PREFIX) }.forEach { remove(it) }
                }
            }
        }
    }

    private companion object {
        const val REQUEST_PREFIX = "request."
        const val KEY_REMOVED = "removed_package"
        const val KEY_INSTALL = "install_package"
        const val KEY_LABEL = "label"
        const val KEY_STARTED_AT = "started_at"
        const val KEY_QUEUE_ID = "queue_id"
        const val KEY_REQUIRES_UPDATE_OWNERSHIP = "requires_update_ownership"
        const val KEY_GITHUB_OWNER = "github_owner"
        const val KEY_GITHUB_NAME = "github_name"
        const val KEY_GITHUB_ASSET_ID = "github_asset_id"
        const val KEY_GITHUB_ASSET_NAME = "github_asset_name"
        const val KEY_GITHUB_ASSET_SIZE = "github_asset_size"
        const val KEY_GITHUB_ASSET_URL = "github_asset_url"
        const val KEY_GITHUB_ASSET_DIGEST = "github_asset_digest"
        const val KEY_GITHUB_RELEASE_ID = "github_release_id"
    }
}
