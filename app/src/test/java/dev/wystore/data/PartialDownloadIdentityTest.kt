package dev.wystore.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PartialDownloadIdentityTest {

    private val url = "https://example.org/releases/app-release.apk"

    @Test
    fun `the same file from the same place may be resumed`() {
        val stored = PartialDownloadIdentity.marker(url, 2_730_086)

        assertTrue(PartialDownloadIdentity.matches(stored, url, 2_730_086))
    }

    /**
     * The case this exists for: a release that keeps the asset name and changes the bytes. Same
     * row, same directory, same partial file - and a prefix of the old one, which must not be
     * offered as a prefix of the new.
     */
    @Test
    fun `a new release at the same address is not the same file`() {
        val stored = PartialDownloadIdentity.marker(url, 2_730_086)

        assertFalse(PartialDownloadIdentity.matches(stored, url, 2_900_100))
    }

    @Test
    fun `the same size from a different address is not the same file either`() {
        val stored = PartialDownloadIdentity.marker(url, 2_730_086)

        assertFalse(
            PartialDownloadIdentity.matches(stored, "https://example.org/other.apk", 2_730_086)
        )
    }

    /** No marker is not evidence of anything, and is not read as permission to resume. */
    @Test
    fun `a partial with no marker is not trusted`() {
        assertFalse(PartialDownloadIdentity.matches(null, url, 2_730_086))
        assertFalse(PartialDownloadIdentity.matches("", url, 2_730_086))
    }

    /** A digest, not the address: the cache has no business holding URLs. */
    @Test
    fun `the marker keeps no address`() {
        val marker = PartialDownloadIdentity.marker(url, 2_730_086)

        assertEquals(64, marker.length)
        assertTrue(marker.matches(Regex("[0-9a-f]{64}")))
        assertFalse(marker.contains("example.org"))
    }
}
