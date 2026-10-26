package dev.wystore.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.google.gson.Gson
import dev.wystore.data.StoreSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GooglePlayUpdateSettingsTest {
    private val gson = Gson()
    private val key = booleanPreferencesKey("continue_checks_after_google_play_update")

    @Test
    fun `stored permission to continue checks is read from DataStore`() {
        val prefs = mutablePreferencesOf(key to true)
        val settings = SettingsCodec.read(prefs)

        assertEquals(true, gson.toJsonTree(settings).asJsonObject
            .get("continueChecksAfterGooglePlayUpdate")?.asBoolean)
    }

    @Test
    fun `continuing checks stays disabled until the user enables it`() {
        val settings = SettingsCodec.read(mutablePreferencesOf())

        assertEquals(false, gson.toJsonTree(settings).asJsonObject
            .get("continueChecksAfterGooglePlayUpdate")?.asBoolean)
    }

    @Test
    fun `backup preference survives both settings mappings and persistence`() {
        val restored = gson.fromJson(
            """{"continueChecksAfterGooglePlayUpdate":true}""",
            StoreSettings::class.java
        ).toAppSettings()
        val prefs = mutablePreferencesOf()
        SettingsCodec.write(prefs, AppSettings(), restored)

        assertTrue(prefs[key] == true)
        val exported = SettingsCodec.read(prefs).toStoreSettings()
        assertEquals(true, gson.toJsonTree(exported).asJsonObject
            .get("continueChecksAfterGooglePlayUpdate")?.asBoolean)
    }
}
