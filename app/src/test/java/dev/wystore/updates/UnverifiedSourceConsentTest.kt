package dev.wystore.updates

import dev.wystore.data.VerificationError
import dev.wystore.updates.model.QueueErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which refusal the user is allowed to answer, and how narrowly their answer is remembered.
 *
 * RuStore states the fingerprint an app should be signed with; for ru.gdemoideti.parent it states
 * one and serves a file signed with another, which is the case this exists for. Consent covers
 * that package at that version and nothing else - it is not a setting, and the next version asks
 * again.
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

    @Test
    fun `consent names one version of one package`() {
        assertEquals("ru.gdemoideti.parent@2012071", UnverifiedSourceConsent.key("ru.gdemoideti.parent", 2012071))
        assertFalse(
            UnverifiedSourceConsent.key("ru.gdemoideti.parent", 2012071) ==
                UnverifiedSourceConsent.key("ru.gdemoideti.parent", 2012072)
        )
    }
}
