package dev.wystore.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.google.gson.Gson

class StoreBackupTest {

    @Test
    fun backupSerializationAndDeserialization() {
        val originalBackup = WyStoreBackup(
            version = 2,
            exportedAt = 1723140000000L,
            settings = StoreSettings(
                wifiOnly = false,
                requiresCharging = true,
                allowMobileData = true,
                backgroundRootUpdates = true,
                updateIntervalHours = 12
            ),
            ruStoreCompatibility = RuStoreCompatibility(
                apiVersionCode = 110502L,
                verifiedVersionName = "1.105.0.2",
                verifiedVersionCode = 1105002L,
                verifiedAt = 1723140000000L
            ),
            managedApps = listOf(
                ManagedApp(
                    packageName = "ru.vk.store",
                    label = "RuStore",
                    pinnedDigests = setOf("661f20828ef780de0b79bc59f26a30864316355f30e4f91cfa14a20791839914"),
                    autoUpdate = true
                ),
                ManagedApp(
                    packageName = "com.github.sample",
                    label = "Sample GitHub App",
                    pinnedDigests = setOf("abcdef1234567890"),
                    autoUpdate = true,
                    source = ManagedSource.GITHUB,
                    githubRepository = GitHubRepository("owner", "repo")
                )
            ),
            githubRepositories = listOf(
                GitHubRepository("owner", "repo"),
                GitHubRepository("topjohnwu", "Magisk")
            )
        )

        val gson = Gson()
        val json = gson.toJson(originalBackup)
        assertNotNull(json)
        assertTrue(json.contains("ru.vk.store"))
        assertTrue(json.contains("Sample GitHub App"))
        assertTrue(json.contains("topjohnwu"))

        val restored = gson.fromJson(json, WyStoreBackup::class.java)
        assertEquals(originalBackup.version, restored.version)
        assertEquals(originalBackup.settings.updateIntervalHours, restored.settings.updateIntervalHours)
        assertEquals(originalBackup.ruStoreCompatibility.apiVersionCode, restored.ruStoreCompatibility.apiVersionCode)
        assertEquals(2, restored.managedApps.size)
        assertEquals(2, restored.githubRepositories.size)
        assertEquals("RuStore", restored.managedApps.first().label)
    }

    @Test
    fun legacyUsedVersionCodeFieldRestoresAsApiVersionCode() {
        val json = """{"version":1,"ruStoreCompatibility":{"usedVersionCode":1105002}}"""

        val restored = Gson().fromJson(json, WyStoreBackup::class.java)

        assertEquals(1105002L, restored.ruStoreCompatibility.apiVersionCode)
        assertEquals(
            111103L,
            RuStoreApiCompatibilityPolicy.codeFromBackup(restored.version, restored.ruStoreCompatibility.apiVersionCode)
        )
    }

    @Test
    fun version2BackupRestoresWithDefaultSettings() {
        val json = """{"version":2,"settings":{"updateIntervalHours":12,"allowMobileData":true}}"""
        val restored = Gson().fromJson(json, WyStoreBackup::class.java)
        assertEquals(2, restored.version)
        assertEquals(QueueMode.SMART_PROMPTS, restored.settings.queueMode)
        assertEquals(false, restored.settings.rootBackgroundDownloadsEnabled)
        assertEquals(7, restored.settings.artifactRetentionDays)
        assertEquals(dev.wystore.settings.ThemeMode.SYSTEM, restored.settings.themeMode)
        assertEquals(dev.wystore.settings.AppLanguage.SYSTEM, restored.settings.language)
        // The default: Wy Store's own palette, the wallpaper's colours one switch away.
        assertEquals(false, restored.settings.dynamicColorEnabled)
        assertEquals(true, restored.settings.githubEnabled)
    }

    @Test
    fun version3BackupRestoresWithUiSettings() {
        val json = """{"version":3,"settings":{"updateIntervalHours":12,"allowMobileData":true,"themeMode":"DARK","language":"RU","dynamicColorEnabled":false,"githubEnabled":false}}"""
        val restored = Gson().fromJson(json, WyStoreBackup::class.java)
        assertEquals(3, restored.version)
        assertEquals(dev.wystore.settings.ThemeMode.DARK, restored.settings.themeMode)
        assertEquals(dev.wystore.settings.AppLanguage.RU, restored.settings.language)
        assertEquals(false, restored.settings.dynamicColorEnabled)
        assertEquals(false, restored.settings.githubEnabled)
    }

    @Test
    fun aChoiceThisBuildDoesNotKnowRestoresAsTheDefault() {
        // A backup from a newer build may name an accent, theme or language this one lacks. Gson
        // reads an unknown enum constant as null, behind a non-null type, and saving it threw.
        val json = """{"version":3,"settings":{"accent":"PLUM","themeMode":"SEPIA","language":"XX","queueMode":"NEW"}}"""
        val restored = Gson().fromJson(json, WyStoreBackup::class.java).settings.withKnownChoices()

        assertEquals(dev.wystore.settings.AccentPreset.ORANGE, restored.accent)
        assertEquals(dev.wystore.settings.ThemeMode.SYSTEM, restored.themeMode)
        assertEquals(dev.wystore.settings.AppLanguage.SYSTEM, restored.language)
        assertEquals(QueueMode.SMART_PROMPTS, restored.queueMode)
    }

    @Test
    fun aKnownAccentSurvivesTheBackup() {
        val json = """{"version":3,"settings":{"accent":"TEAL"}}"""
        val restored = Gson().fromJson(json, WyStoreBackup::class.java).settings.withKnownChoices()

        assertEquals(dev.wystore.settings.AccentPreset.TEAL, restored.accent)
    }
}
