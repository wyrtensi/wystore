package dev.wystore.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SigningContinuityTest {

    private val oldKey = "a".repeat(64)
    private val newKey = "b".repeat(64)
    private val stranger = "c".repeat(64)

    @Test
    fun theSameSignerUpdatesItself() {
        assertTrue(
            SigningContinuity.allows(
                installedDigests = setOf(oldKey),
                archiveDigests = setOf(oldKey),
                archiveLineage = emptySet()
            )
        )
    }

    /** The case this exists for: the developer rotated their key and Android accepts the update. */
    @Test
    fun aRotatedKeyThatProvesItsHistoryIsAccepted() {
        assertTrue(
            SigningContinuity.allows(
                installedDigests = setOf(oldKey),
                archiveDigests = setOf(newKey),
                archiveLineage = setOf(oldKey, newKey)
            )
        )
    }

    @Test
    fun aDifferentKeyWithNoHistoryIsRefused() {
        assertFalse(
            SigningContinuity.allows(
                installedDigests = setOf(oldKey),
                archiveDigests = setOf(stranger),
                archiveLineage = emptySet()
            )
        )
    }

    /** A lineage that leaves out what the phone actually trusts proves nothing about it. */
    @Test
    fun aHistoryThatDoesNotCoverTheInstalledCertificateIsRefused() {
        assertFalse(
            SigningContinuity.allows(
                installedDigests = setOf(oldKey),
                archiveDigests = setOf(newKey),
                archiveLineage = setOf(stranger, newKey)
            )
        )
    }

    @Test
    fun unreadableSignaturesOnEitherSideAreRefused() {
        assertFalse(SigningContinuity.allows(emptySet(), setOf(oldKey), setOf(oldKey)))
        assertFalse(SigningContinuity.allows(setOf(oldKey), emptySet(), setOf(oldKey)))
    }
}
