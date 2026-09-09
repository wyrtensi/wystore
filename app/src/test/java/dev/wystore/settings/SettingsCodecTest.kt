package dev.wystore.settings

import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsCodecTest {

    /**
     * The whole point: one switch, one key. Persisting all of them is what froze a phone on the
     * defaults of the day it was first configured.
     */
    @Test
    fun savingOneSwitchWritesOnlyThatSwitch() {
        val prefs = mutablePreferencesOf()
        val current = SettingsCodec.read(prefs)

        SettingsCodec.write(prefs, current, current.copy(themeMode = ThemeMode.DARK))

        assertEquals(setOf(SettingsCodec.KEY_THEME_MODE), prefs.asMap().keys)
        assertEquals(ThemeMode.DARK, SettingsCodec.read(prefs).themeMode)
    }

    /**
     * An untouched switch has no opinion stored, so a default changed in a later version reaches a
     * phone that already has the app - without anything overwriting settings on its owner's behalf.
     */
    @Test
    fun anUntouchedSwitchFollowsTheDefaultAfterAnUpdate() {
        val prefs = mutablePreferencesOf()
        val current = SettingsCodec.read(prefs)
        SettingsCodec.write(prefs, current, current.copy(quietHoursEnabled = true))

        assertFalse(prefs.contains(SettingsCodec.KEY_AUTO_INSTALL_UPDATES))
        assertFalse(prefs.contains(SettingsCodec.KEY_REQUIRES_CHARGING))

        // Reading resolves an absent key against AppSettings, which is where defaults live, so
        // raising one there is all a later version has to do.
        val read = SettingsCodec.read(prefs)
        assertEquals(AppSettings().autoInstallUpdates, read.autoInstallUpdates)
        assertEquals(AppSettings().requiresCharging, read.requiresCharging)
    }

    @Test
    fun aSwitchTheUserTurnedOffStaysOffAndIsRecorded() {
        val prefs = mutablePreferencesOf()
        val current = SettingsCodec.read(prefs)
        assertTrue(current.autoInstallUpdates)

        SettingsCodec.write(prefs, current, current.copy(autoInstallUpdates = false))

        assertTrue(prefs.contains(SettingsCodec.KEY_AUTO_INSTALL_UPDATES))
        assertFalse(SettingsCodec.read(prefs).autoInstallUpdates)
    }

    /** Turning a switch back to the value it started at is still an answer, so it is stored. */
    @Test
    fun turningASwitchBackOnRecordsTheChoice() {
        val prefs = mutablePreferencesOf()
        var current = SettingsCodec.read(prefs)
        SettingsCodec.write(prefs, current, current.copy(autoInstallUpdates = false))

        current = SettingsCodec.read(prefs)
        SettingsCodec.write(prefs, current, current.copy(autoInstallUpdates = true))

        assertEquals(true, prefs[SettingsCodec.KEY_AUTO_INSTALL_UPDATES])
    }

    /**
     * `wifi_only` and `allow_mobile_data` are two names for one choice, held apart. A write that
     * touched only one of them would leave the pair disagreeing; the network choice sets both.
     */
    @Test
    fun choosingTheNetworkSetsBothSidesOfIt() {
        val prefs = mutablePreferencesOf(SettingsCodec.KEY_ALLOW_MOBILE_DATA to true)
        val current = SettingsCodec.read(prefs)
        assertTrue(current.allowMobileData)

        SettingsCodec.write(prefs, current, current.copy(wifiOnly = true, allowMobileData = false))

        val read = SettingsCodec.read(prefs)
        assertTrue(read.wifiOnly)
        assertFalse(read.allowMobileData)
    }

    /** An unreadable enum name must not take the whole record down with it. */
    @Test
    fun anUnknownStoredEnumFallsBackToTheDefault() {
        val prefs = mutablePreferencesOf(SettingsCodec.KEY_LANGUAGE to "KLINGON")

        assertEquals(AppSettings().language, SettingsCodec.read(prefs).language)
    }
}
