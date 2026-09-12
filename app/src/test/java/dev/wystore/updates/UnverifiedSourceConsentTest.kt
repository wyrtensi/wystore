package dev.wystore.updates

import dev.wystore.data.VerificationError
import dev.wystore.updates.model.QueueErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which refusal the user is allowed to answer, and how narrowly their answer is remembered.
 *
 * RuStore states the fingerprint an app should be signed with; for ru.gdemoideti.parent it states
 * one and serves a file signed with another, which is the case this exists for. What the answer
 * covers is pinned in [UnverifiedSourceStore]: the pair of fingerprints the user was shown, not
 * the app or the version.
 */
class UnverifiedSourceConsentTest {

    @Test
    fun `only the source fingerprint is answerable`() {
        assertTrue(
            UnverifiedSourceConsent.isAnswerable(
                QueueErrorCode.SIGNATURE,
                VerificationError.SOURCE_FINGERPRINT_MISMATCH.name
            )
        )
    }

    @Test
    fun `a signature that does not match the installed app is not`() {
        // Android refuses this one itself, and no answer here would change that.
        assertFalse(
            UnverifiedSourceConsent.isAnswerable(
                QueueErrorCode.SIGNATURE,
                VerificationError.SIGNATURE_MISMATCH.name
            )
        )
    }

    @Test
    fun `nor is anything that merely failed to arrive`() {
        assertFalse(UnverifiedSourceConsent.isAnswerable(QueueErrorCode.NETWORK, "timed out"))
        assertFalse(UnverifiedSourceConsent.isAnswerable(QueueErrorCode.INTERNAL, null))
        assertFalse(UnverifiedSourceConsent.isAnswerable(null, null))
    }


    /**
     * A real pair, from the app this exists for: RuStore advertises 5b2be9db... for
     * ru.gdemoideti.parent and serves a file signed 26d83b54...
     */
    private val advertised = "5b2be9db4566d49179b6b4ca383fbafd0e8012ea5d3926def98505090eb2d241"
    private val archive = "26d83b5434593692fbf1c7260a641ff9a8b791a2192832817618533992693da1"

    @Test
    fun `an answer covers the file it was given for`() {
        val record = UnverifiedSourceConsent.record(advertised, archive)

        assertEquals(archive, UnverifiedSourceConsent.acceptedArchiveDigest(record, advertised))
    }

    /**
     * The queue row of an app that is not installed yet carries no version, so a version-shaped key
     * would read the same for every later first install of that package. What the answer is tied to
     * is the claim it was about: the source correcting its catalogue asks again.
     */
    @Test
    fun `it does not cover a different claim by the source`() {
        val record = UnverifiedSourceConsent.record(advertised, archive)

        assertNull(
            UnverifiedSourceConsent.acceptedArchiveDigest(
                record,
                "0bc473ae8f9488d0d610a3434880d11c198a81b91867a456ab373150e62d807e"
            )
        )
    }

    @Test
    fun `and nothing accepted covers nothing`() {
        assertNull(UnverifiedSourceConsent.acceptedArchiveDigest(null, advertised))
        assertNull(UnverifiedSourceConsent.acceptedArchiveDigest("", advertised))
        assertNull(UnverifiedSourceConsent.acceptedArchiveDigest(advertised, advertised))
        assertNull(UnverifiedSourceConsent.acceptedArchiveDigest(record(), null))
    }

    @Test
    fun `a refusal with only one side of it is not a record`() {
        assertNull(UnverifiedSourceConsent.record(advertised, null))
        assertNull(UnverifiedSourceConsent.record(null, archive))
        assertNull(UnverifiedSourceConsent.record(advertised, " "))
    }

    private fun record() = UnverifiedSourceConsent.record(advertised, archive)
}
