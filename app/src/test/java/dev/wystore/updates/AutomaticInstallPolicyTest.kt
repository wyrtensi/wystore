package dev.wystore.updates

import dev.wystore.data.InstallSource
import dev.wystore.data.InstalledApp
import dev.wystore.data.ManagedApp
import dev.wystore.data.ManagedSource
import dev.wystore.data.StoreSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomaticInstallPolicyTest {
    private val app = ManagedApp("test.app", "Test", emptySet())
    private val installed = InstalledApp("test.app", "Test", "1", 1, 0, InstallSource.WY_STORE, emptySet())
    private val settings = StoreSettings(autoInstallUpdates = true)

    @Test fun `a removed app waits for an explicit new install request`() {
        assertFalse(AutomaticInstallPolicy.mayOffer(app, null, settings, userRequested = false))
        assertFalse(AutomaticInstallPolicy.mayOffer(null, null, settings, userRequested = false))
        assertTrue(AutomaticInstallPolicy.mayOffer(null, null, settings, userRequested = true))
    }

    @Test fun `disabling automatic installation stops old requests including requested downloads`() {
        assertFalse(AutomaticInstallPolicy.mayOffer(app, installed,
            settings.copy(autoInstallUpdates = false), userRequested = true))
    }

    @Test fun `an explicit ownership replacement still offers Android confirmation with automatic installs off`() {
        assertTrue(AutomaticInstallPolicy.mayOffer(null, null,
            settings.copy(autoInstallUpdates = false, autoInstallNewApps = false),
            userRequested = true, requiresUpdateOwnership = true))
    }

    @Test fun `an ownership marker without explicit intent does not grant installation permission`() {
        assertFalse(AutomaticInstallPolicy.mayOffer(null, null,
            settings.copy(autoInstallUpdates = false),
            userRequested = false, requiresUpdateOwnership = true))
    }

    @Test fun `a legacy replacement offers confirmation without enabling automatic installs`() {
        val disabled = settings.copy(autoInstallUpdates = false, autoInstallNewApps = false)
        assertTrue(AutomaticInstallPolicy.mayOffer(null, null, disabled,
            userRequested = true, requiresUserConfirmation = true))
        assertFalse(AutomaticInstallPolicy.mayOffer(null, null, disabled,
            userRequested = false, requiresUserConfirmation = true))
    }

    @Test fun `a handover offer does not grant the background installer silent installation permission`() {
        val disabled = settings.copy(autoInstallUpdates = false)
        assertTrue(AutomaticInstallPolicy.mayOffer(app, null, disabled, true, requiresUpdateOwnership = true))
        // BackgroundInstaller does not supply the confirmation-only intent.
        assertFalse(AutomaticInstallPolicy.mayOffer(app, null, disabled, true))
        assertTrue(SilentUpdatePolicy.allows(enabled = true, sdkInt = 34,
            isUpdate = true, installerOfRecord = "app.wystore", ownPackageName = "app.wystore"))
        assertFalse(SilentUpdatePolicy.allows(enabled = true, sdkInt = 34,
            isUpdate = true, installerOfRecord = "app.wystore", ownPackageName = "app.wystore",
            requiresUpdateOwnership = true))
    }

    @Test fun `excluded apps and disabled GitHub sources stop old automatic requests`() {
        assertFalse(AutomaticInstallPolicy.mayOffer(app.copy(autoUpdate = false), installed, settings, false))
        assertFalse(AutomaticInstallPolicy.mayOffer(app.copy(source = ManagedSource.GITHUB), installed,
            settings.copy(githubEnabled = false), false))
        assertTrue(AutomaticInstallPolicy.mayOffer(app.copy(source = ManagedSource.GITHUB), installed,
            settings.copy(githubEnabled = false), true))
    }

    @Test fun `recorded Play attribution pauses an automatic ready file`() {
        assertFalse(AutomaticInstallPolicy.mayOffer(app.copy(googlePlayUpdateDetected = true), installed, settings, false))
        assertTrue(AutomaticInstallPolicy.mayOffer(app.copy(googlePlayUpdateDetected = true), installed,
            settings.copy(continueChecksAfterGooglePlayUpdate = true), false))
    }
}
