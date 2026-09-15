package dev.wystore.root

import dev.wystore.data.StoreSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Losing root used to switch silent install off for good, and any save made before su had answered
 * after a restart did the same, so the user had to find the switch and turn it on again.
 */
class RootSettingsGuardTest {

    private val allOn = StoreSettings(
        backgroundRootUpdates = true,
        rootSilentInstallEnabled = true,
        rootSilentUninstallEnabled = true,
        rootBackgroundDownloadsEnabled = true
    )

    @Test
    fun `switches already on survive a save while root is missing or not yet asked`() {
        listOf(false, null).forEach { root ->
            val saved = RootSettingsGuard.apply(allOn.copy(wifiOnly = false), current = allOn, rootAvailable = root)
            assertEquals(allOn.copy(wifiOnly = false), saved)
        }
    }

    @Test
    fun `a switch cannot be turned on without root`() {
        listOf(false, null).forEach { root ->
            val saved = RootSettingsGuard.apply(allOn, current = StoreSettings(), rootAvailable = root)
            assertFalse(saved.rootSilentInstallEnabled)
            assertFalse(saved.backgroundRootUpdates)
            assertFalse(saved.rootSilentUninstallEnabled)
            assertFalse(saved.rootBackgroundDownloadsEnabled)
        }
    }

    @Test
    fun `with root a switch turns on, and without it still turns off`() {
        val on = RootSettingsGuard.apply(allOn, current = StoreSettings(), rootAvailable = true)
        assertTrue(on.rootSilentInstallEnabled && on.backgroundRootUpdates)
        assertTrue(on.rootSilentUninstallEnabled && on.rootBackgroundDownloadsEnabled)

        val off = RootSettingsGuard.apply(StoreSettings(), current = allOn, rootAvailable = false)
        assertEquals(StoreSettings(), off)
    }
}
