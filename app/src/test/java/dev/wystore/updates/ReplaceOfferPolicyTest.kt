package dev.wystore.updates

import dev.wystore.data.VerificationError
import dev.wystore.updates.model.QueueErrorCode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When "uninstall and install" is worth offering.
 *
 * The queue offered it for every signature failure, including on an app that is not on the phone:
 * there was nothing to uninstall. It is the one action here that costs the user their data, so it
 * is offered only where it changes the outcome.
 */
class ReplaceOfferPolicyTest {

    @Test
    fun `an installed app the new file cannot go over is the case it exists for`() {
        assertTrue(
            ReplaceOfferPolicy.offersReplace(
                QueueErrorCode.SIGNATURE,
                VerificationError.SIGNATURE_MISMATCH.name,
                appInstalled = true
            )
        )
        assertTrue(
            ReplaceOfferPolicy.offersReplace(
                QueueErrorCode.SIGNATURE,
                VerificationError.WRONG_PACKAGE_FOR_UPDATE.name,
                appInstalled = true
            )
        )
    }

    @Test
    fun `with nothing installed there is nothing to uninstall`() {
        assertFalse(
            ReplaceOfferPolicy.offersReplace(
                QueueErrorCode.SIGNATURE,
                VerificationError.SIGNATURE_MISMATCH.name,
                appInstalled = false
            )
        )
    }

    @Test
    fun `and the refusals an uninstall does not clear are not offered it`() {
        listOf(
            VerificationError.NOT_AN_APK,
            VerificationError.NO_BASE_APK,
            VerificationError.MIXED_VERSIONS,
            VerificationError.WRONG_PACKAGE,
            VerificationError.UNREADABLE_SIGNATURE,
            VerificationError.MIXED_SIGNATURES,
            VerificationError.SOURCE_FINGERPRINT_MISMATCH
        ).forEach { error ->
            assertFalse(
                "$error survives a reinstall",
                ReplaceOfferPolicy.offersReplace(QueueErrorCode.SIGNATURE, error.name, appInstalled = true)
            )
        }
    }

    @Test
    fun `a row that does not name its check is not offered it either`() {
        assertFalse(ReplaceOfferPolicy.offersReplace(QueueErrorCode.SIGNATURE, null, appInstalled = true))
        assertFalse(ReplaceOfferPolicy.offersReplace(QueueErrorCode.SIGNATURE, "something else", appInstalled = true))
        assertFalse(ReplaceOfferPolicy.offersReplace(QueueErrorCode.NETWORK, null, appInstalled = true))
    }

    /** The two answers to the same pair never stand on the same row. */
    @Test
    fun `a question for the user is never a reinstall`() {
        VerificationError.entries.forEach { error ->
            listOf(true, false).forEach { installed ->
                val answerable = UnverifiedSourceConsent.isAnswerable(QueueErrorCode.SIGNATURE, error.name)
                val replace = ReplaceOfferPolicy.offersReplace(QueueErrorCode.SIGNATURE, error.name, installed)
                assertFalse("$error offers both", answerable && replace)
            }
        }
    }
}
