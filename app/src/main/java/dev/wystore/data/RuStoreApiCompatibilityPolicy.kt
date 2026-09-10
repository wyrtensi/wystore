package dev.wystore.data

data class RuStoreApiAttempt<T>(
    val versionCode: Long,
    val value: T,
    val accepted: Boolean
)

/**
 * The `ruStoreVerCode` header the source expects, and what to do when it stops accepting it.
 *
 * The value is written here rather than discovered: the app used to download the official RuStore
 * client and read the number out of its manifest, which meant fetching eighty megabytes to learn
 * six digits. It is derived from the published version name of the client - 1.109.1.0 - the way the
 * source itself derives it.
 */
object RuStoreApiCompatibilityPolicy {
    /** From RuStore 1.109.1.0, the client published at rustore.ru at the time of this release. */
    const val DEFAULT_VERSION_CODE = 110_910L
    const val ATTEMPTS_PER_CANDIDATE = 3
    private val FALLBACK_VERSION_CODES = listOf(1_000_000L, 247L)
    private val REJECTED_HTTP_CODES = setOf(417, 419)

    /**
     * What to try, in order. The baked-in code first; the fallbacks exist only so that a rejected
     * header does not take the catalogue down until the next release of this app.
     */
    fun candidates(): List<Long> =
        (listOf(DEFAULT_VERSION_CODE) + FALLBACK_VERSION_CODES).filter { it > 0L }.distinct()

    fun migrateLegacyCode(versionCode: Long): Long =
        versionCode.takeIf { it in 247L..1_100_000L } ?: DEFAULT_VERSION_CODE

    fun codeFromBackup(backupVersion: Int, versionCode: Long): Long =
        if (backupVersion <= 1) migrateLegacyCode(versionCode) else versionCode

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
