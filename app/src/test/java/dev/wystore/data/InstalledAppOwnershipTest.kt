package dev.wystore.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstalledAppOwnershipTest {
    @Test
    fun `the Android update owner takes precedence over the recorded installer`() {
        val app = installed(installer = "com.android.vending", owner = "app.wystore")

        assertEquals("app.wystore", app.updateResponsiblePackageName)
        assertEquals("com.android.vending", app.installerPackageName)
        assertEquals(InstallSource.WY_STORE, InstalledAppOwnership.source(app.updateResponsiblePackageName, "app.wystore"))
    }

    @Test
    fun `Play ownership keeps Play classification even after another installer updated the app`() {
        val app = installed(installer = "app.wystore", owner = "com.android.vending")

        assertEquals(InstallSource.GOOGLE_PLAY, InstalledAppOwnership.source(app.updateResponsiblePackageName, "app.wystore"))
    }

    @Test
    fun `missing and blank owners fall back to the installer on older Android versions`() {
        listOf(null, "", "  ").forEach { owner ->
            assertEquals("ru.vk.store", installed(installer = "ru.vk.store", owner = owner).updateResponsiblePackageName)
        }
        assertNull(installed(installer = "  ", owner = "").updateResponsiblePackageName)
    }

    @Test
    fun `a preinstalled app shows its current owner when Android supplies one`() {
        val app = installed(installer = "android", owner = "com.xiaomi.discover", preinstalled = true)

        assertEquals("com.xiaomi.discover", app.updateResponsiblePackageName)
        assertFalse(InstalledAppOwnership.showPreinstalledLabel(app))
    }

    @Test
    fun `a preinstalled app without an owner keeps the preinstalled label`() {
        assertTrue(InstalledAppOwnership.showPreinstalledLabel(installed(installer = "android", preinstalled = true)))
        assertTrue(InstalledAppOwnership.showPreinstalledLabel(installed(owner = "  ", preinstalled = true)))
        assertFalse(InstalledAppOwnership.showPreinstalledLabel(installed()))
    }

    private fun installed(installer: String? = null, owner: String? = null, preinstalled: Boolean = false) = InstalledApp(
        packageName = "com.example.app",
        label = "Example",
        versionName = "1",
        versionCode = 1,
        lastUpdateTime = 0,
        source = InstallSource.OTHER,
        signingDigests = emptySet(),
        installerPackageName = installer,
        preinstalled = preinstalled,
        updateOwnerPackageName = owner
    )
}
