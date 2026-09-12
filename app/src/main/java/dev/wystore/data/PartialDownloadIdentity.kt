package dev.wystore.data

import java.security.MessageDigest

/**
 * What a half-downloaded file is a prefix of.
 *
 * Resuming asks the server for the bytes after the ones on disk, and appends them. That is only
 * correct while the file being fetched is the same file the partial came from - and the partial is
 * named after its slot, `artifact_0.part`, which says nothing about that. A queue row keyed by
 * package, version and source normally guarantees it, but a GitHub row whose version is not known
 * reuses one row across releases: same row, same directory, same name, different asset. Resuming
 * there would staple an old prefix to a new tail and end at exactly the expected length, so the
 * size check would pass and only the APK parser would notice.
 *
 * So the partial carries a marker saying where it came from, and is thrown away when it does not
 * match. Stored as a digest rather than the URL itself: equality is all this needs, and the cache
 * has no business holding addresses.
 */
object PartialDownloadIdentity {

    /** The marker for a file being fetched from [url] that should weigh [expectedBytes]. */
    fun marker(url: String, expectedBytes: Long): String =
        MessageDigest.getInstance("SHA-256")
            .digest("$url\n$expectedBytes".toByteArray())
            .joinToString("") { "%02x".format(it) }

    /**
     * Whether bytes already on disk may be treated as a prefix of this download.
     *
     * An absent marker means a partial written before this app recorded them, or by something
     * else. It is not evidence of anything, so it is not trusted.
     */
    fun matches(stored: String?, url: String, expectedBytes: Long): Boolean =
        stored != null && stored == marker(url, expectedBytes)
}
