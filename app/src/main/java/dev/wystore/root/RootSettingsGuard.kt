package dev.wystore.root

import dev.wystore.data.StoreSettings

/**
 * What a save may do to the switches that need root.
 *
 * A switch that needs root can be turned on only while su has said yes. One that is already on stays
 * on when root is missing or not yet asked: su answers late after a restart, and a root manager can
 * refuse once and allow again. Every root path checks su again before it acts and falls back to
 * Android's own dialog, so a switch left on costs nothing while root is away. Turning off is always
 * allowed.
 */
object RootSettingsGuard {

    fun apply(requested: StoreSettings, current: StoreSettings, rootAvailable: Boolean?): StoreSettings {
        fun keep(wanted: Boolean, was: Boolean) = wanted && (rootAvailable == true || was)
        val silentInstall = keep(requested.rootSilentInstallEnabled, current.rootSilentInstallEnabled)
        return requested.copy(
            // The two names are one switch on the screen, so they move together.
            backgroundRootUpdates = silentInstall,
            rootSilentInstallEnabled = silentInstall,
            rootSilentUninstallEnabled = keep(requested.rootSilentUninstallEnabled, current.rootSilentUninstallEnabled),
            rootBackgroundDownloadsEnabled = keep(requested.rootBackgroundDownloadsEnabled, current.rootBackgroundDownloadsEnabled)
        )
    }
}
