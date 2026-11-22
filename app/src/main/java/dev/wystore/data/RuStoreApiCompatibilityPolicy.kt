package dev.wystore.data

data class RuStoreApiAttempt<T>(
    val versionCode: Long,
    val value: T,
    val accepted: Boolean
)

/**
 * The `ruStoreVerCode` header the source expects, and what to do when it stops accepting it.
 *
 * The backend uses the official RuStore client's 7-digit manifest `versionCode` (for example
 * `1_114_003` for RuStore 1.114.0.3). Earlier builds stripped dots from `versionName` into a
 * 6-digit number (`111_204`), which fell below RuStore's `1_059_001` threshold for `.zip`
 * transport compression and forced the server onto legacy raw `.apk` links (larger downloads and
 * missing CDN splits such as `com.vkontakte.android` returning HTTP 404).
 *
 * A daily GitHub Action publishes the latest verified code to `rustore-version.json` on the
 * `release-reports` branch, which [RuStoreVersionSync] caches locally so updates follow RuStore
 * without downloading the 80 MB official APK on every phone.
 */
object RuStoreApiCompatibilityPolicy {
    /** Manifest versionCode of RuStore 1.114.0.3. */
    const val DEFAULT_VERSION_CODE = 1_114_003L

    /** Lowest `ruStoreVerCode` where RuStore serves `.zip`-wrapped splits and universal APKs. */
    const val MIN_ZIP_TRANSPORT_VERSION_CODE = 1_059_001L

    private const val MAX_REASONABLE_VERSION_CODE = 99_999_999L

    const val ATTEMPTS_PER_CANDIDATE = 3
    private val FALLBACK_VERSION_CODES = listOf(1_112_004L, 1_000_000L, 247L)
    private val REJECTED_HTTP_CODES = setOf(417, 419)

    fun isUsableRemoteCode(versionCode: Long): Boolean =
        versionCode in MIN_ZIP_TRANSPORT_VERSION_CODE..MAX_REASONABLE_VERSION_CODE

    /**
     * What to try, in order. A newer remote code synced from GitHub goes first, followed by the
     * baked-in code and bounded fallbacks so a rejected header does not take the catalogue down.
     */
    fun candidates(remoteVersionCode: Long? = null): List<Long> {
        val usable = remoteVersionCode?.takeIf(::isUsableRemoteCode)
        return buildList {
            if (usable != null && usable >= DEFAULT_VERSION_CODE) add(usable)
            add(DEFAULT_VERSION_CODE)
            if (usable != null && usable < DEFAULT_VERSION_CODE) add(usable)
            addAll(FALLBACK_VERSION_CODES)
        }.filter { it > 0L }.distinct()
    }

    fun migrateLegacyCode(versionCode: Long): Long =
        versionCode.takeIf { isUsableRemoteCode(it) && it >= DEFAULT_VERSION_CODE } ?: DEFAULT_VERSION_CODE

    fun codeFromBackup(backupVersion: Int, versionCode: Long): Long =
        if (backupVersion <= 1) migrateLegacyCode(versionCode)
        else versionCode.takeIf { isUsableRemoteCode(it) && it >= DEFAULT_VERSION_CODE } ?: DEFAULT_VERSION_CODE

    fun <T> execute(
        candidates: List<Long>,
        request: (Long) -> T,
        statusCode: (T) -> Int,
        discard: (T) -> Unit
    ): RuStoreApiAttempt<T> {
        require(candidates.isNotEmpty()) { "At least one RuStore API version candidate is required" }
        candidates.forEachIndexed { candidateIndex, versionCode ->
            repeat(ATTEMPTS_PER_CANDIDATE) { attemptIndex ->
                val value = request(versionCode)
                val rejected = statusCode(value) in REJECTED_HTTP_CODES
                if (!rejected) return RuStoreApiAttempt(versionCode, value, accepted = true)
                val isLast = candidateIndex == candidates.lastIndex && attemptIndex == ATTEMPTS_PER_CANDIDATE - 1
                if (isLast) return RuStoreApiAttempt(versionCode, value, accepted = false)
                discard(value)
            }
        }
        error("RuStore API compatibility attempts ended unexpectedly")
    }
}
