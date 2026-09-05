package dev.wystore.updates

import dev.wystore.data.StoreSettings
import dev.wystore.settings.AppSettings
import dev.wystore.settings.toAppSettings
import dev.wystore.settings.toStoreSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Silent root install was implemented and unit-tested but had no production caller, so the setting
 * did nothing. These lock down the two halves that made it unreachable: the mode decision itself,
 * and the settings round-trip that carries the switch to the worker that reads it.
 */
class InstallModePolicyReachabilityTest {

    @Test
    fun silentInstallNeedsBothTheSettingAndActualRoot() {
        assertEquals(
            InstallMode.SILENT_ROOT,
            InstallModePolicy.choose(silentRootInstallEnabled = true, rootAvailable = true)
        )
        assertEquals(
            InstallMode.USER_CONFIRMATION,
            InstallModePolicy.choose(silentRootInstallEnabled = true, rootAvailable = false)
        )
        assertEquals(
            InstallMode.USER_CONFIRMATION,
            InstallModePolicy.choose(silentRootInstallEnabled = false, rootAvailable = true)
        )
        assertEquals(
            InstallMode.USER_CONFIRMATION,
            InstallModePolicy.choose(silentRootInstallEnabled = false, rootAvailable = false)
        )
    }

    @Test
    fun theSilentInstallSwitchSurvivesTheSettingsRoundTrip() {
        // The worker reads StoreSettings.rootSilentInstallEnabled; the screen writes AppSettings.
        val enabled = AppSettings(rootSilentInstallEnabled = true).toStoreSettings()
        assertTrue(enabled.rootSilentInstallEnabled)
        assertTrue("the settings screen toggles both aliases together", enabled.backgroundRootUpdates)
        assertTrue(enabled.toAppSettings().rootSilentInstallEnabled)

        val disabled = AppSettings(rootSilentInstallEnabled = false).toStoreSettings()
        assertEquals(false, disabled.rootSilentInstallEnabled)
        assertEquals(false, disabled.toAppSettings().rootSilentInstallEnabled)
    }

    @Test
    fun rootBackgroundDownloadsSurviveTheSettingsRoundTrip() {
        val settings: StoreSettings = AppSettings(rootBackgroundDownloadsEnabled = true).toStoreSettings()

        assertTrue(settings.rootBackgroundDownloadsEnabled)
        assertTrue(settings.toAppSettings().rootBackgroundDownloadsEnabled)
    }
}
