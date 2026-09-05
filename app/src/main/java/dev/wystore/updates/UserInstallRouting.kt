package dev.wystore.updates

enum class UserInstallRoute {
    LEGACY_SINGLE_APK,
    PACKAGE_INSTALLER_SESSION
}

object UserInstallRouting {
    fun select(sdkInt: Int, artifactCount: Int): UserInstallRoute {
        require(artifactCount > 0) { "At least one APK artifact is required" }
        return if (sdkInt <= 27 && artifactCount == 1) {
            UserInstallRoute.LEGACY_SINGLE_APK
        } else {
            UserInstallRoute.PACKAGE_INSTALLER_SESSION
        }
    }
}
