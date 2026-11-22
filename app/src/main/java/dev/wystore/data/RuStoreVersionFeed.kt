package dev.wystore.data

import com.google.gson.JsonParser

data class RemoteRuStoreVersion(
    val versionCode: Long,
    val versionName: String?
)

/**
 * Parses and validates the daily RuStore version feed published by GitHub Actions.
 *
 * Keeping the official RuStore client's manifest `versionCode` in a small JSON file on GitHub lets
 * Wy Store stay current with RuStore's backend without downloading the full 80 MB RuStore APK onto
 * every user's phone or waiting for a new Wy Store release when RuStore raises its header floor.
 */
object RuStoreVersionFeed {
    const val FEED_URL =
        "https://raw.githubusercontent.com/wyrtensi/wystore/release-reports/rustore-version.json"

    /** Refresh once a day during normal use. */
    const val REFRESH_INTERVAL_MS = 24L * 60 * 60 * 1000

    /** Wait an hour before retrying when GitHub could not be reached. */
    const val FAILURE_BACKOFF_MS = 60L * 60 * 1000

    /** Minimum wait between forced refreshes triggered by a 417/419 rejection from RuStore. */
    const val FORCED_COOLDOWN_MS = 5L * 60 * 1000

    private const val MAX_SUPPORTED_SCHEMA = 1

    fun parse(json: String): RemoteRuStoreVersion? = runCatching {
        val obj = JsonParser.parseString(json).takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val schema = obj.get("schemaVersion")?.takeUnless { it.isJsonNull }?.asInt ?: 1
        if (schema !in 1..MAX_SUPPORTED_SCHEMA) return null
        val verifiedZip = obj.get("verifiedZipTransport")?.takeUnless { it.isJsonNull }?.asBoolean
        if (verifiedZip == false) return null
        val code = obj.get("versionCode")?.takeUnless { it.isJsonNull }?.asLong ?: return null
        if (!RuStoreApiCompatibilityPolicy.isUsableRemoteCode(code)) return null
        val name = obj.get("versionName")?.takeUnless { it.isJsonNull }?.asString?.trim()
            ?.takeIf { it.isNotEmpty() }
        RemoteRuStoreVersion(versionCode = code, versionName = name)
    }.getOrNull()

    fun shouldRefresh(
        nowMs: Long,
        lastSuccessAtMs: Long?,
        lastAttemptAtMs: Long?,
        force: Boolean
    ): Boolean {
        if (force) {
            return lastAttemptAtMs == null || (nowMs - lastAttemptAtMs) >= FORCED_COOLDOWN_MS
        }
        if (lastSuccessAtMs != null && (nowMs - lastSuccessAtMs) < REFRESH_INTERVAL_MS) {
            return false
        }
        if (lastAttemptAtMs != null && (nowMs - lastAttemptAtMs) < FAILURE_BACKOFF_MS) {
            return false
        }
        return true
    }
}
