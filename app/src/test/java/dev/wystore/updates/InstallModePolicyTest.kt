package dev.wystore.updates

import org.junit.Assert.assertEquals
import org.junit.Test

class InstallModePolicyTest {
    @Test
    fun rootAndEnabledSilentInstallUsesRoot() {
        assertEquals(
            InstallMode.SILENT_ROOT,
            InstallModePolicy.choose(silentRootInstallEnabled = true, rootAvailable = true)
        )
    }

    @Test
    fun disabledSilentInstallRequiresUserConfirmationEvenWithRoot() {
        assertEquals(
            InstallMode.USER_CONFIRMATION,
            InstallModePolicy.choose(silentRootInstallEnabled = false, rootAvailable = true)
        )
    }

    @Test
    fun missingRootFallsBackToUserConfirmation() {
        assertEquals(
            InstallMode.USER_CONFIRMATION,
            InstallModePolicy.choose(silentRootInstallEnabled = true, rootAvailable = false)
        )
    }
}
