package dev.wystore.data

/**
 * Why a source request failed, as a code rather than a sentence.
 *
 * Two things forced this. The messages were Russian strings thrown from a layer with no access to
 * resources, so they surfaced verbatim in an English interface. And [classifyThrowable] decided
 * whether a failure was retryable by matching Russian substrings against the message, which means
 * translating any one of them would silently have downgraded a storage or integrity failure to a
 * generic network error.
 */
enum class SourceError {
    GITHUB_REPOSITORY_NOT_FOUND,
    GITHUB_RATE_LIMITED,
    GITHUB_UNAVAILABLE,
    GITHUB_INVALID_URL,
    GITHUB_NO_DOWNLOAD_LOCATION,
    GITHUB_TOO_MANY_REDIRECTS,

    RUSTORE_UNAVAILABLE,
    RUSTORE_EMPTY_RESPONSE,
    RUSTORE_API_REJECTED,
    RUSTORE_NO_DOWNLOAD_LINK,
    RUSTORE_BUNDLE_INVALID,

    FORMAT_CHANGED,
    WRONG_PACKAGE,
    INVALID_PACKAGE_NAME,
    INVALID_SECTION,
    EMPTY_SECTION,
    INCOMPATIBLE_ANDROID,

    UNTRUSTED_HOST,
    INVALID_ARTIFACT_URL,
    ARTIFACT_TOO_LARGE,
    ARTIFACT_SIZE_MISMATCH,
    ARTIFACT_INTEGRITY_MISMATCH,
    ARTIFACT_WRITE_FAILED,
    DOWNLOAD_FAILED
}
