package dev.wystore.updates

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the system installer's answer means for the queue row that was waiting on it.
 *
 * Before this the answer was never read: success showed up later as a package broadcast, and a
 * declined dialog left the row INSTALLING - holding the only install slot - until the next reboot.
 */
class LegacyInstallOutcomeTest {

    private val handedOverAt = 1_000L

    private fun decide(
        resultCode: Int,
        installResult: Int? = null,
        installedVersionCode: Long? = null,
        installedUpdatedAt: Long? = null
    ) = LegacyInstallOutcome.decide(
        resultCode = resultCode,
        installResult = installResult,
        targetVersionCode = 200L,
        installedVersionCode = installedVersionCode,
        installedUpdatedAt = installedUpdatedAt,
        handedOverAt = handedOverAt
    )

    @Test
    fun `the installer saying it installed is believed`() {
        assertEquals(LegacyInstallOutcome.Installed, decide(resultCode = LegacyInstallOutcome.RESULT_OK))
    }

    @Test
    fun `the package on the device outranks an installer that does not report back`() {
        // A vendor installer may ignore EXTRA_RETURN_RESULT and just close; the device still knows.
        assertEquals(
            LegacyInstallOutcome.Installed,
            decide(
                resultCode = LegacyInstallOutcome.RESULT_CANCELED,
                installedVersionCode = 200L,
                installedUpdatedAt = 1_500L
            )
        )
    }

    @Test
    fun `the same version installed before the handover is not this install`() {
        // A reinstall the user asked for: the version already matched, so only the time can tell.
        assertEquals(
            LegacyInstallOutcome.Declined,
            decide(
                resultCode = LegacyInstallOutcome.RESULT_CANCELED,
                installedVersionCode = 200L,
                installedUpdatedAt = 500L
            )
        )
    }

    @Test
    fun `closing the dialog puts the download back, it is not an error`() {
        assertEquals(LegacyInstallOutcome.Declined, decide(resultCode = LegacyInstallOutcome.RESULT_CANCELED))
    }

    @Test
    fun `a reported failure carries the installer's code`() {
        assertEquals(
            LegacyInstallOutcome.Failed(-7),
            decide(resultCode = LegacyInstallOutcome.RESULT_FIRST_USER, installResult = -7)
        )
    }

    @Test
    fun `a failure code is named, and an unknown one still shows its number`() {
        assertEquals("INSTALL_FAILED_NO_MATCHING_ABIS (-113)", LegacyInstallOutcome.describe(-113))
        assertEquals("system installer code -999", LegacyInstallOutcome.describe(-999))
    }
}
