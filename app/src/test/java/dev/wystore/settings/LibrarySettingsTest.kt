package dev.wystore.settings

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.google.gson.Gson
import dev.wystore.data.ManagedApp
import dev.wystore.data.ManagedSource
import dev.wystore.data.WyStoreBackup
import org.junit.Assert.*
import org.junit.Test

class LibrarySettingsTest {
    @Test fun `library setting is independent of GitHub and survives codec roundtrip`() {
        val prefs = mutablePreferencesOf()
        val defaults = SettingsCodec.read(prefs)
        assertTrue(defaults.libraryEnabled)
        SettingsCodec.write(prefs, defaults, defaults.copy(libraryEnabled = false))
        assertFalse(SettingsCodec.read(prefs).libraryEnabled)
        assertTrue(SettingsCodec.read(prefs).githubEnabled)
        val mapped = SettingsCodec.read(prefs).toStoreSettings().toAppSettings()
        assertFalse(mapped.libraryEnabled)
    }

    @Test fun `backup retains source revision without erasing Play attribution`() {
        val original = ManagedApp("org.example.library", "Example", setOf("abc"),
            source = ManagedSource.WY_STORE, googlePlayUpdateDetected = true, sourceRevision = 7)
        val restored = Gson().fromJson(Gson().toJson(WyStoreBackup(managedApps = listOf(original))), WyStoreBackup::class.java)
        assertEquals(original, restored.managedApps.single())
        val legacy = Gson().fromJson("{\"managedApps\":[{\"packageName\":\"org.example.library\",\"label\":\"Example\",\"source\":\"RUSTORE\"}]}", WyStoreBackup::class.java)
        assertEquals(0L, legacy.managedApps.single().sourceRevision)
        assertTrue(legacy.settings.libraryEnabled)
    }
}
