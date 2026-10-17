package dev.wystore.data

import android.content.pm.ApplicationInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstalledPackageLookupTest {

    @Test
    fun anInstalledAppIsOnTheDeviceFrozenOrNot() {
        // A frozen (hidden) app keeps FLAG_INSTALLED; only the lookup has to ask for it.
        assertTrue(InstalledPackageLookup.isOnDevice(ApplicationInfo.FLAG_INSTALLED))
        assertTrue(InstalledPackageLookup.isOnDevice(ApplicationInfo.FLAG_INSTALLED or ApplicationInfo.FLAG_HAS_CODE))
    }

    @Test
    fun anAppRemovedWithItsDataKeptIsNot() {
        // The same flag that reveals frozen apps returns these too; offering an update for one
        // would reinstall something the user removed.
        assertFalse(InstalledPackageLookup.isOnDevice(ApplicationInfo.FLAG_HAS_CODE))
        assertFalse(InstalledPackageLookup.isOnDevice(null))
    }

    @Test
    fun frozenIsInstalledButHiddenFromAPlainLookup() {
        assertTrue(InstalledPackageLookup.isFrozen(visibleWithoutFlag = false, onDevice = true))
        assertFalse(InstalledPackageLookup.isFrozen(visibleWithoutFlag = true, onDevice = true))
        assertFalse(InstalledPackageLookup.isFrozen(visibleWithoutFlag = false, onDevice = false))
    }
}
