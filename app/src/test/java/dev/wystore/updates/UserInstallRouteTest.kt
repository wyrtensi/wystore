package dev.wystore.updates

import org.junit.Assert.assertEquals
import org.junit.Test

class UserInstallRouteTest {
    @Test
    fun `android 8 uses legacy system installer for a single apk`() {
        assertEquals(UserInstallRoute.LEGACY_SINGLE_APK, UserInstallRouting.select(sdkInt = 26, artifactCount = 1))
        assertEquals(UserInstallRoute.LEGACY_SINGLE_APK, UserInstallRouting.select(sdkInt = 27, artifactCount = 1))
    }

    @Test
    fun `android 8 keeps package installer sessions for split packages`() {
        assertEquals(UserInstallRoute.PACKAGE_INSTALLER_SESSION, UserInstallRouting.select(sdkInt = 27, artifactCount = 2))
    }

    @Test
    fun `android 9 and newer keep package installer sessions`() {
        assertEquals(UserInstallRoute.PACKAGE_INSTALLER_SESSION, UserInstallRouting.select(sdkInt = 28, artifactCount = 1))
        assertEquals(UserInstallRoute.PACKAGE_INSTALLER_SESSION, UserInstallRouting.select(sdkInt = 36, artifactCount = 1))
    }

    @Test
    fun `a device that refuses sessions installs a single apk through the system installer`() {
        assertEquals(
            UserInstallRoute.LEGACY_SINGLE_APK,
            UserInstallRouting.select(sdkInt = 29, artifactCount = 1, sessionsRefused = true)
        )
        assertEquals(
            UserInstallRoute.LEGACY_SINGLE_APK,
            UserInstallRouting.select(sdkInt = 36, artifactCount = 1, sessionsRefused = true)
        )
    }

    @Test
    fun `split parts on such a device have to be fetched again as one apk`() {
        // The install intent takes exactly one file, and a session is already known to be refused.
        assertEquals(
            UserInstallRoute.WHOLE_APK_REQUIRED,
            UserInstallRouting.select(sdkInt = 29, artifactCount = 3, sessionsRefused = true)
        )
    }
}
