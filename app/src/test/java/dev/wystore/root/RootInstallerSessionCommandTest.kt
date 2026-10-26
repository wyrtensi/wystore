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
    fun `a profile install targets the app user rather than the primary user`() {
        assertEquals(
            "pm install-create --user 10 -i app.wystore --update-ownership",
            RootInstaller.createSessionCommand(update = false, installerPackageName = "app.wystore", sdkInt = 34, userId = 10)
        )
    }

    @Test
    fun `a profile uninstall removes the app from the same user as its install`() {
        assertEquals(
            "pm uninstall --user 10 'com.example.app'",
            RootInstaller.uninstallCommand("com.example.app", userId = 10)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an invalid package cannot reach the uninstall shell command`() {
        RootInstaller.uninstallCommand("com.example.app; reboot", userId = 10)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a special user cannot reach the uninstall shell command`() {
        RootInstaller.uninstallCommand("com.example.app", userId = -1)
    }

    @Test
    fun `the process uid determines the profile that owns the app`() {
        assertEquals(0, RootInstaller.userIdForUid(0))
        assertEquals(0, RootInstaller.userIdForUid(10_123))
        assertEquals(1, RootInstaller.userIdForUid(100_123))
        assertEquals(10, RootInstaller.userIdForUid(1_010_123))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a negative uid cannot target an Android user`() {
        RootInstaller.userIdForUid(-1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a special or negative user id never reaches the shell`() {
        RootInstaller.createSessionCommand(update = false, installerPackageName = "app.wystore", sdkInt = 34, userId = -1)
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
