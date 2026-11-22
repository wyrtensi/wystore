package dev.wystore.data

/**
 * Decides when a failed RuStore split-APK transfer should be retried as a single universal APK.
 *
 * RuStore serves split APKs by default, which saves bandwidth on multi-ABI apps. Occasionally a
 * split artifact on the CDN returns 404 or 410 while the universal APK returned for
 * `withoutSplits = true` is still published and reachable. Retrying once without splits recovers
 * those downloads without masking general network or verification errors.
 */
object RuStoreSplitFallbackPolicy {
    private val MISSING_ARTIFACT_HTTP = Regex("""\bHTTP\s+(404|410)\b""")

    fun shouldFallbackToWholeApk(
        requestedWithoutSplits: Boolean,
        artifactCount: Int,
        error: Throwable
    ): Boolean {
        if (requestedWithoutSplits || artifactCount <= 1) return false
        val formatError = error as? SourceFormatException ?: return false
        if (formatError.error != SourceError.DOWNLOAD_FAILED) return false
        return MISSING_ARTIFACT_HTTP.containsMatchIn(formatError.message.orEmpty())
    }
}
