package dev.wystore.root

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The `pm install-create` line a root install starts with.
 *
 * It named no installer, so every app Wy Store installed through root was recorded as installed by
 * nobody: its page offered to hand its updates to Wy Store straight after Wy Store installed it.
 */
class RootInstallerSessionCommandTest {

    @Test
    fun `a first install names Wy Store as the installer and claims updates on Android 14`() {
        assertEquals(
            "pm install-create --user 0 -i app.wystore --update-ownership",
            RootInstaller.createSessionCommand(update = false, installerPackageName = "app.wystore", sdkInt = 34)
        )
    }

    @Test
    fun `an update replaces the installed app`() {
        assertEquals(
            "pm install-create --user 0 -i app.wystore -r --update-ownership",
            RootInstaller.createSessionCommand(update = true, installerPackageName = "app.wystore", sdkInt = 35)
        )
    }

    @Test
    fun `before Android 14 the ownership flag is left out, because pm there refuses it`() {
        assertEquals(
            "pm install-create --user 0 -i app.wystore -r",
            RootInstaller.createSessionCommand(update = true, installerPackageName = "app.wystore", sdkInt = 33)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an installer name that is not a package name never reaches the shell`() {
        RootInstaller.createSessionCommand(update = false, installerPackageName = "app.wystore; reboot", sdkInt = 34)
    }
}
