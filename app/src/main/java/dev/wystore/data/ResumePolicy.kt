package dev.wystore.data

/**
 * When an interrupted download may pick up where it stopped.
 *
 * A partial file is only useful if it is a prefix of the file being fetched. These rules decide
 * that from sizes and from the reason the previous attempt failed, with no I/O of their own, so
 * each one is directly testable.
 */
object ResumePolicy {

    /**
     * Bytes the next request may skip, given what is on disk and what the artifact should weigh.
     *
     * Zero means start over: an empty file has nothing to resume, and a partial file that already
     * matches or exceeds the expected size is not a prefix of anything — it is evidence that the
     * expected size changed or that the file was written by a different download.
     */
    fun resumableBytes(onDiskBytes: Long, expectedBytes: Long): Long = when {
        onDiskBytes <= 0L -> 0L
        expectedBytes <= 0L -> 0L
        onDiskBytes >= expectedBytes -> 0L
        else -> onDiskBytes
    }

    /**
     * Whether a failure invalidates the bytes already written.
     *
     * A dropped connection leaves a good prefix and is exactly what resuming is for. A size or
     * integrity failure says the file on disk is not what it claims, and keeping it would make
     * every later attempt resume onto corrupt data.
     */
    fun discardsPartialFile(error: Throwable): Boolean {
        val sourceError = (error as? SourceFormatException)?.error ?: return false
        return sourceError in DESTRUCTIVE_ERRORS
    }

    private val DESTRUCTIVE_ERRORS = setOf(
        SourceError.ARTIFACT_SIZE_MISMATCH,
        SourceError.ARTIFACT_INTEGRITY_MISMATCH,
        SourceError.ARTIFACT_TOO_LARGE,
        SourceError.INVALID_ARTIFACT_URL,
        SourceError.UNTRUSTED_HOST
    )
}
