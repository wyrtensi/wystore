package dev.wystore.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallerNamesTest {

    @Test
    fun `known stores get a name short enough for a badge`() {
        assertEquals("Google Play", InstallerNames.short("com.android.vending"))
        assertEquals("GetApps", InstallerNames.short("com.xiaomi.mipicks"))
        assertNull(InstallerNames.short("com.example.store"))
    }

    @Test
    fun `an APK opened by hand reads as manual, a store does not`() {
        assertTrue(InstallerNames.isManual(null))
        assertTrue(InstallerNames.isManual("com.google.android.packageinstaller"))
        assertFalse(InstallerNames.isManual("ru.vk.store"))
    }
}
