package dev.wystore.data

data class RuStoreApiAttempt<T>(
    val versionCode: Long,
    val value: T,
    val accepted: Boolean
)

object RuStoreApiCompatibilityPolicy {
    const val DEFAULT_VERSION_CODE = 110_802L
    const val ATTEMPTS_PER_CANDIDATE = 3
    private val FALLBACK_VERSION_CODES = listOf(1_000_000L, 247L)
    private val REJECTED_HTTP_CODES = setOf(417, 419)

    fun fromOfficialVersionName(versionName: String?): Long? {
        val parts = versionName?.split('.') ?: return null
        if (parts.size < 2 || parts.any { it.isEmpty() || it.any { character -> !character.isDigit() } }) return null
        return parts.joinToString(separator = "").toLongOrNull()?.takeIf { it > 0L }
    }

    fun candidates(preferred: Long, discovered: Long? = null): List<Long> =
        listOfNotNull(discovered, preferred, *FALLBACK_VERSION_CODES.toTypedArray())
            .filter { it > 0L }
            .distinct()

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
