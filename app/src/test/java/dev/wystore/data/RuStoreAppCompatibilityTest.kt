package dev.wystore.data

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RuStoreAppCompatibilityTest {
    @Test
    fun parsesNumericMinimumSdkFromOverallInfo() {
        val body = JsonParser.parseString(
            """
            {
              "appId": 2688703,
              "packageName": "com.avito.android",
              "versionName": "231.5",
              "versionCode": 3502,
              "minSdkVersion": 28
            }
            """.trimIndent()
        ).asJsonObject

        val app = body.toStoreApp()

        assertEquals(28, app.minSdkVersion)
        assertEquals("Android 9 (SDK 28)", app.minAndroidVersion)
    }

    @Test
    fun rejectsLatestVersionWhenDeviceSdkIsBelowMinimum() {
        try {
            AndroidSdkCompatibility.requireSupported(minSdkVersion = 28, deviceSdkVersion = 27)
            fail("Expected incompatible Android version to be rejected")
        } catch (error: SourceFormatException) {
            assertTrue(error.message.orEmpty().contains("Android 9 (SDK 28)"))
            assertTrue(error.message.orEmpty().contains("Android 8.1 (SDK 27)"))
        }
    }

    @Test
    fun acceptsVersionWhenDeviceMeetsMinimumOrSourceOmitsIt() {
        AndroidSdkCompatibility.requireSupported(minSdkVersion = 27, deviceSdkVersion = 27)
        AndroidSdkCompatibility.requireSupported(minSdkVersion = null, deviceSdkVersion = 27)
    }
}
