package dev.wystore.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsDefaultsMigrationTest {

    @Test
    fun aPhoneThatHasNeverSeenTheMigrationNeedsIt() {
        assertTrue(SettingsDefaultsMigration.needsUpgrade(0))
    }

    @Test
    fun aPhoneAlreadyAtTheCurrentRevisionIsLeftAlone() {
        assertFalse(SettingsDefaultsMigration.needsUpgrade(SettingsDefaultsMigration.REVISION))
    }

    /**
     * The point of the migration: these four were written to disk as the old defaults, so raising
     * them in code alone would have changed nothing for anyone who already had the app.
     */
    @Test
    fun theOldDefaultsAreRaisedToTheNewOnes() {
        val stored = AppSettings(
            autoDownloadUpdates = false,
            autoInstallUpdates = false,
            autoInstallNewApps = false,
            requiresCharging = true
        )

        val upgraded = SettingsDefaultsMigration.upgrade(stored)

        assertTrue(upgraded.autoDownloadUpdates)
        assertTrue(upgraded.autoInstallUpdates)
        assertTrue(upgraded.autoInstallNewApps)
        assertFalse(upgraded.requiresCharging)
    }

    @Test
    fun everythingElseSurvivesUntouched() {
        val stored = AppSettings(
            wifiOnly = false,
            allowMobileData = true,
            updateIntervalHours = 6,
            themeMode = ThemeMode.DARK,
            language = AppLanguage.EN,
            githubEnabled = false,
            quietHoursEnabled = true,
            selfUpdateEnabled = false,
            artifactRetentionDays = 30
        )

        val upgraded = SettingsDefaultsMigration.upgrade(stored)

        assertFalse(upgraded.wifiOnly)
        assertTrue(upgraded.allowMobileData)
        assertEquals(6L, upgraded.updateIntervalHours)
        assertEquals(ThemeMode.DARK, upgraded.themeMode)
        assertEquals(AppLanguage.EN, upgraded.language)
        assertFalse(upgraded.githubEnabled)
        assertTrue(upgraded.quietHoursEnabled)
        assertFalse(upgraded.selfUpdateEnabled)
        assertEquals(30, upgraded.artifactRetentionDays)
    }
}
