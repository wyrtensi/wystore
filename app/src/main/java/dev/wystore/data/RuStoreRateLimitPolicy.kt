package dev.wystore.data

/**
 * What to do when RuStore answers 429, "too many requests".
 *
 * The answer is about pace, not about the app asked for. It used to be read as any other 4xx - "no
 * such app in RuStore" - so a check that asked about forty apps in a row reported a dozen of them as
 * gone from the store, and remembered them as missing. A wait and another try is what the answer
 * asks for; after that the app counts as unreachable for this round, never as missing.
 */
object RuStoreRateLimitPolicy {

    const val TOO_MANY_REQUESTS = 429

    /** Waits used when the source does not say how long to wait, one per retry. */
    private val DEFAULT_DELAYS_MILLIS = listOf(3_000L, 10_000L)

    /** A stated wait longer than this is not waited out inside one request. */
    const val MAX_DELAY_MILLIS = 30_000L

    /**
     * The pause between two apps in an update check. A check used to fire its requests back to back,
     * and on a phone with forty apps that alone was enough for the source to start refusing.
     */
    const val CHECK_PACE_MILLIS = 400L

    /**
     * How long to wait before retry number [retry] (0 for the first), or null to give up. A
     * [retryAfterMillis] the source stated is honoured up to [MAX_DELAY_MILLIS]; beyond that the
     * round moves on rather than holding a worker for minutes.
     */
    fun delayBeforeRetry(retry: Int, retryAfterMillis: Long?): Long? {
        if (retry !in DEFAULT_DELAYS_MILLIS.indices) return null
        if (retryAfterMillis != null) {
            if (retryAfterMillis > MAX_DELAY_MILLIS) return null
            return retryAfterMillis.coerceAtLeast(DEFAULT_DELAYS_MILLIS[retry])
        }
        return DEFAULT_DELAYS_MILLIS[retry]
    }

    /** The error an unsuccessful answer reports once any retries are spent. */
    fun errorFor(statusCode: Int): SourceError = when (statusCode) {
        TOO_MANY_REQUESTS -> SourceError.RUSTORE_RATE_LIMITED
        in 400..499 -> SourceError.RUSTORE_NOT_FOUND
        else -> SourceError.RUSTORE_UNAVAILABLE
    }
}
