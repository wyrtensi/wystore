package dev.wystore.updates

import android.content.Context
import dev.wystore.data.GitHubAsset
import dev.wystore.data.GitHubRelease
import dev.wystore.data.TransferFailure
import dev.wystore.updates.model.QueueErrorCode

/**
 * The release file a person picked for a queue row.
 *
 * The queue row carries the release, not the file, so the worker chose the ABI-preferred build
 * whatever was tapped: "install the universal build" after the arm64 one failed fetched the arm64
 * one again. The choice is kept beside the row rather than in it, which needs no schema change.
 */
class GitHubAssetChoice(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun remember(queueId: String, assetId: Long) {
        prefs.edit().putLong(queueId, assetId).apply()
    }

    fun forget(queueId: String) {
        prefs.edit().remove(queueId)
            .remove(exactKey(queueId, "id"))
            .remove(exactKey(queueId, "name"))
            .remove(exactKey(queueId, "size"))
            .remove(exactKey(queueId, "url"))
            .remove(exactKey(queueId, "digest"))
            .apply()
    }

    fun chosen(queueId: String): Long? = prefs.getLong(queueId, NONE).takeIf { it != NONE }

    fun rememberExact(queueId: String, asset: GitHubAsset) {
        check(
            prefs.edit()
                .putLong(queueId, asset.id)
                .putLong(exactKey(queueId, "id"), asset.id)
                .putString(exactKey(queueId, "name"), asset.name)
                .putLong(exactKey(queueId, "size"), asset.sizeBytes)
                .putString(exactKey(queueId, "url"), asset.downloadUrl)
                .putString(exactKey(queueId, "digest"), asset.digest)
                .commit()
        ) { "Could not persist the selected GitHub handover asset" }
    }

    fun exact(queueId: String): GitHubAsset? {
        val name = prefs.getString(exactKey(queueId, "name"), null) ?: return null
        val url = prefs.getString(exactKey(queueId, "url"), null) ?: return null
        if (!prefs.contains(exactKey(queueId, "id")) || !prefs.contains(exactKey(queueId, "size"))) return null
        return GitHubAsset(
            id = prefs.getLong(exactKey(queueId, "id"), NONE),
            name = name,
            sizeBytes = prefs.getLong(exactKey(queueId, "size"), 0),
            downloadUrl = url,
            digest = prefs.getString(exactKey(queueId, "digest"), null)
        )
    }

    private fun exactKey(queueId: String, field: String): String = "exact:$queueId:$field"

    suspend fun prepareDownload(
        queueId: String,
        requiresExactSelection: Boolean,
        resolveReleaseAsset: suspend () -> GitHubAsset?
    ): GitHubAsset? = forDownload(requiresExactSelection, exact(queueId), resolveReleaseAsset)?.also { asset ->
        // Verification can merge an older real package row into this placeholder and inherit its
        // handover requirement. A later retry then needs this row's actual selected file too.
        rememberExact(queueId, asset)
    }

    companion object {
        private const val PREFS = "github_asset_choice"
        private const val NONE = Long.MIN_VALUE

        fun shouldRememberExact(
            requiresUpdateOwnership: Boolean,
            existingRequiresUpdateOwnership: Boolean,
            automatic: Boolean
        ): Boolean = requiresUpdateOwnership || (!automatic && existingRequiresUpdateOwnership)

        suspend fun forDownload(
            requiresExactSelection: Boolean,
            exactSelection: GitHubAsset?,
            resolveReleaseAsset: suspend () -> GitHubAsset?
        ): GitHubAsset? = if (requiresExactSelection) {
            exactSelection ?: throw TransferFailure(
                retryable = false,
                code = QueueErrorCode.SOURCE_CHANGED,
                detail = "The selected GitHub handover asset is no longer available"
            )
        } else resolveReleaseAsset()

        /** Legacy id-only rows may be upgraded, but never by picking another file or release. */
        suspend fun forReplacement(
            chosenId: Long?,
            exactSelection: GitHubAsset?,
            releaseId: Long?,
            releases: suspend () -> List<GitHubRelease>
        ): GitHubAsset {
            if (chosenId != null && exactSelection?.id == chosenId) return exactSelection
            if (chosenId == null || exactSelection != null || releaseId == null) unavailableReplacement()
            return releases().firstOrNull { it.id == releaseId }?.assets
                ?.firstOrNull { it.id == chosenId } ?: unavailableReplacement()
        }

        private fun unavailableReplacement(): Nothing = throw TransferFailure(
            retryable = false, code = QueueErrorCode.SOURCE_CHANGED,
            detail = "The selected GitHub replacement asset is no longer available"
        )

        /** The picked file when the release still has it; otherwise the caller's own choice. */
        fun pick(candidates: List<GitHubAsset>, chosenId: Long?, fallback: () -> GitHubAsset?): GitHubAsset? =
            chosenId?.let { id -> candidates.firstOrNull { it.id == id } } ?: fallback()
    }
}
