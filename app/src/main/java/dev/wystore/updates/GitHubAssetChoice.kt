package dev.wystore.updates

import android.content.Context
import dev.wystore.data.GitHubAsset

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
        prefs.edit().remove(queueId).apply()
    }

    fun chosen(queueId: String): Long? = prefs.getLong(queueId, NONE).takeIf { it != NONE }

    companion object {
        private const val PREFS = "github_asset_choice"
        private const val NONE = Long.MIN_VALUE

        /** The picked file when the release still has it; otherwise the caller's own choice. */
        fun pick(candidates: List<GitHubAsset>, chosenId: Long?, fallback: () -> GitHubAsset?): GitHubAsset? =
            chosenId?.let { id -> candidates.firstOrNull { it.id == id } } ?: fallback()
    }
}
