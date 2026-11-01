package dev.wystore.updates

import dev.wystore.data.GooglePlayUpdatePolicy
import dev.wystore.data.InstalledApp
import dev.wystore.data.ManagedApp
import dev.wystore.data.ManagedSource
import dev.wystore.data.StoreSettings

/** Applies current choices to durable install requests, including files downloaded before them. */
object AutomaticInstallPolicy {
    fun mayOffer(
        managed: ManagedApp?,
        installed: InstalledApp?,
        settings: StoreSettings,
        userRequested: Boolean,
        requiresUpdateOwnership: Boolean = false,
        requiresUserConfirmation: Boolean = false
    ): Boolean {
        // This grants an offer of Android's dialog, never permission for a silent installer.
        if (userRequested && (requiresUpdateOwnership || requiresUserConfirmation)) return true
        if (!settings.autoInstallUpdates) return false
        if (userRequested) return true
        if (installed == null) return false
        if (managed == null) return true
        if (!managed.autoUpdate || (managed.source == ManagedSource.GITHUB && !settings.githubEnabled) ||
            (managed.source == ManagedSource.WY_STORE && !settings.libraryEnabled)) return false
        return !GooglePlayUpdatePolicy.isPaused(managed, installed, settings.continueChecksAfterGooglePlayUpdate)
    }
}
