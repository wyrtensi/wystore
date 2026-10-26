package dev.wystore.data

import dev.wystore.updates.QueueOrigin

/** Android's recorded installer is evidence of attribution, not a complete install history. */
object GooglePlayUpdatePolicy {
    fun hasGooglePlayUpdate(managed: ManagedApp, installed: InstalledApp?): Boolean =
        managed.googlePlayUpdateDetected || installed?.installerPackageName == "com.android.vending"

    fun isPaused(managed: ManagedApp, installed: InstalledApp?, continueChecks: Boolean): Boolean =
        hasGooglePlayUpdate(managed, installed) && !managed.forceWyStore && !continueChecks

    fun mayProceed(managed: ManagedApp, installed: InstalledApp?, continueChecks: Boolean, priority: Int): Boolean =
        QueueOrigin.isUserRequested(priority) || (installed != null && !isPaused(managed, installed, continueChecks))
}
