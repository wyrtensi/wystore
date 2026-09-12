package dev.wystore.updates

enum class UserInstallRoute {
    LEGACY_SINGLE_APK,
    PACKAGE_INSTALLER_SESSION,

    /**
     * Split parts on a device that refuses sessions. The install intent takes one file only, so
     * these parts cannot be installed as they are and the app is fetched again as a single APK.
     */
    WHOLE_APK_REQUIRED
}

object UserInstallRouting {
    fun select(sdkInt: Int, artifactCount: Int, sessionsRefused: Boolean = false): UserInstallRoute {
        require(artifactCount > 0) { "At least one APK artifact is required" }
        return when {
            artifactCount == 1 && (sdkInt <= 27 || sessionsRefused) -> UserInstallRoute.LEGACY_SINGLE_APK
            sessionsRefused -> UserInstallRoute.WHOLE_APK_REQUIRED
            else -> UserInstallRoute.PACKAGE_INSTALLER_SESSION
        }
    }
}
