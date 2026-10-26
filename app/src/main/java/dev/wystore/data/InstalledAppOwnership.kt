package dev.wystore.data

/** Android's update owner supersedes the installer only when an owner is recorded. */
object InstalledAppOwnership {
    fun responsiblePackageName(installerPackageName: String?, updateOwnerPackageName: String?): String? =
        updateOwnerPackageName?.takeIf { it.isNotBlank() }
            ?: installerPackageName?.takeIf { it.isNotBlank() }

    fun source(responsiblePackageName: String?, ownPackageName: String): InstallSource = when (responsiblePackageName) {
        "com.android.vending" -> InstallSource.GOOGLE_PLAY
        ownPackageName -> InstallSource.WY_STORE
        else -> InstallSource.OTHER
    }

    fun showPreinstalledLabel(app: InstalledApp): Boolean =
        app.preinstalled && app.updateOwnerPackageName.isNullOrBlank()
}
