package dev.wystore.updates

enum class InstallMode {
    SILENT_ROOT,
    USER_CONFIRMATION
}

object InstallModePolicy {
    fun choose(silentRootInstallEnabled: Boolean, rootAvailable: Boolean): InstallMode =
        if (silentRootInstallEnabled && rootAvailable) InstallMode.SILENT_ROOT
        else InstallMode.USER_CONFIRMATION
}
