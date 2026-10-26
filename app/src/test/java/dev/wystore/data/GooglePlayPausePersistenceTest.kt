package dev.wystore.data

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.assertTrue
import org.junit.Test

class GooglePlayPausePersistenceTest {
    @Test fun `managed backup round trip retains observed Google Play attribution`() {
        val json = """{"packageName":"test.app","label":"Test","pinnedDigests":[],"googlePlayUpdateDetected":true}"""
        val gson = Gson()
        val restored = gson.fromJson(json, ManagedApp::class.java)
        val exported = JsonParser.parseString(gson.toJson(restored)).asJsonObject

        assertTrue("the observed attribution must survive a backup round trip",
            exported.get("googlePlayUpdateDetected")?.asBoolean == true)
    }
}
