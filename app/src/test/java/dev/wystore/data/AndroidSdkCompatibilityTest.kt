package dev.wystore.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidSdkCompatibilityTest {
    @Test
    fun readsTheReleaseNumbersTheCatalogPublishes() {
        assertEquals(28, AndroidSdkCompatibility.sdkForRelease("9"))
        assertEquals(26, AndroidSdkCompatibility.sdkForRelease("8.0"))
        assertEquals(27, AndroidSdkCompatibility.sdkForRelease("8.1"))
        assertEquals(35, AndroidSdkCompatibility.sdkForRelease("15"))
    }

    @Test
    fun acceptsTheReleaseSpelledOutAsAnAndroidVersion() {
        assertEquals(28, AndroidSdkCompatibility.sdkForRelease("Android 9"))
        assertEquals(29, AndroidSdkCompatibility.sdkForRelease(" 10 "))
    }

    @Test
    fun refusesToGuessAtSomethingItDoesNotKnow() {
        assertNull(AndroidSdkCompatibility.sdkForRelease("KitKat"))
        assertNull(AndroidSdkCompatibility.sdkForRelease(""))
    }

    /**
     * The reason this function exists: read as an API level, "Android 9" is SDK 9 and every device
     * ever made compares as new enough, so an app that cannot run here was offered for install.
     */
    @Test
    fun aReleaseNumberIsNotAnApiLevel() {
        assertEquals(28, AndroidSdkCompatibility.sdkForRelease("9"))
        assertEquals("Android 9 (SDK 28)", AndroidSdkCompatibility.label(28))
    }
}
