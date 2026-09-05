package dev.wystore.selfupdate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wy Store cannot use a GitHub release id to decide whether it should update itself, the way it
 * does for other GitHub apps: an id is monotonic but says nothing about which build is running.
 * These are the ordering rules it uses instead.
 */
class SelfUpdateVersionTest {

    @Test
    fun aTagIsParsedWithOrWithoutItsLeadingV() {
        assertEquals(listOf(0, 1, 12), SelfUpdateVersion.parse("v0.1.12"))
        assertEquals(listOf(0, 1, 12), SelfUpdateVersion.parse("0.1.12"))
        assertEquals(listOf(1, 0), SelfUpdateVersion.parse("V1.0"))
    }

    @Test
    fun preReleaseAndBuildSuffixesAreIgnored() {
        assertEquals(listOf(1, 2, 3), SelfUpdateVersion.parse("v1.2.3-rc1"))
        assertEquals(listOf(1, 2, 3), SelfUpdateVersion.parse("1.2.3+build7"))
    }

    @Test
    fun somethingThatIsNotAVersionParsesToNothing() {
        assertNull(SelfUpdateVersion.parse(null))
        assertNull(SelfUpdateVersion.parse(""))
        assertNull(SelfUpdateVersion.parse("   "))
        assertNull(SelfUpdateVersion.parse("nightly"))
        assertNull(SelfUpdateVersion.parse("latest"))
    }

    @Test
    fun missingComponentsCountAsZero() {
        assertEquals(0, SelfUpdateVersion.compare(listOf(1, 2), listOf(1, 2, 0)))
        assertTrue(SelfUpdateVersion.compare(listOf(1, 2, 1), listOf(1, 2)) > 0)
    }

    @Test
    fun numericOrderingNotStringOrdering() {
        // "0.1.9" sorts after "0.1.12" as text, which would have made the app refuse every update
        // past the ninth patch.
        assertTrue(SelfUpdateVersion.isNewer("v0.1.12", "0.1.9"))
        assertFalse(SelfUpdateVersion.isNewer("v0.1.9", "0.1.12"))
    }

    @Test
    fun theSameVersionIsNotAnUpdate() {
        assertFalse(SelfUpdateVersion.isNewer("v0.1.11", "0.1.11"))
        assertFalse(SelfUpdateVersion.isNewer("0.1.11", "0.1.11"))
    }

    @Test
    fun anOlderReleaseIsNeverOffered() {
        assertFalse(SelfUpdateVersion.isNewer("v0.1.10", "0.1.11"))
        assertFalse(SelfUpdateVersion.isNewer("v0.0.9", "1.0.0"))
    }

    @Test
    fun anUnparseableTagIsNeverOffered() {
        // A repository can carry rolling tags that are not releases at all; offering one would
        // hand the user a build the app cannot reason about.
        assertFalse(SelfUpdateVersion.isNewer("nightly", "0.1.11"))
        assertFalse(SelfUpdateVersion.isNewer(null, "0.1.11"))
        assertFalse(SelfUpdateVersion.isNewer("v1.0.0", "not-a-version"))
    }

    @Test
    fun majorVersionsCompareBeforeMinors() {
        assertTrue(SelfUpdateVersion.isNewer("v1.0.0", "0.99.99"))
        assertFalse(SelfUpdateVersion.isNewer("v0.99.99", "1.0.0"))
    }
}
