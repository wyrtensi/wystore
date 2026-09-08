package dev.wystore.updates

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SilentUpdatePolicyTest {

    private fun allows(
        enabled: Boolean = true,
        sdkInt: Int = 34,
        isUpdate: Boolean = true,
        installerOfRecord: String? = "dev.wystore"
    ) = SilentUpdatePolicy.allows(
        enabled = enabled,
        sdkInt = sdkInt,
        isUpdate = isUpdate,
        installerOfRecord = installerOfRecord,
        ownPackageName = "dev.wystore"
    )

    @Test
    fun updatingAnAppWeInstalledNeedsNoDialog() {
        assertTrue(allows())
    }

    @Test
    fun theSettingTurnsItOff() {
        assertFalse(allows(enabled = false))
    }

    @Test
    fun aFirstInstallAlwaysAsks() {
        // Android offers no way around it, and it should not: nothing has been trusted yet.
        assertFalse(allows(isUpdate = false))
    }

    @Test
    fun anAppSomebodyElseInstalledIsNotOursToUpdateSilently() {
        assertFalse(allows(installerOfRecord = "com.android.vending"))
        assertFalse(allows(installerOfRecord = "ru.vk.store"))
    }

    @Test
    fun anAppWithNoInstallerOfRecordAsks() {
        // Sideloaded by hand or pushed over adb: nobody was given the job.
        assertFalse(allows(installerOfRecord = null))
    }

    @Test
    fun theApiIsNotThereBeforeAndroid12() {
        assertFalse(allows(sdkInt = 30))
        assertTrue(allows(sdkInt = 31))
    }
}
