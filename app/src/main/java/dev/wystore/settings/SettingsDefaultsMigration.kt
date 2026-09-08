package dev.wystore.settings

/**
 * Brings a phone that already has settings on disk up to a later set of defaults.
 *
 * [SettingsRepository.update] writes every key, so the first time any single setting was saved the
 * whole record was persisted - including the defaults nobody had chosen. Changing a default in
 * code therefore only ever reaches a fresh install, and every existing phone keeps the old value
 * forever while its owner is told the new one is the default.
 *
 * Only settings whose stored value was never a decision belong here. Downloading and installing an
 * update could not happen at all without root before, so the "off" recorded against them was the
 * old behaviour rather than an answer; the charging requirement guarded a download that never ran.
 */
object SettingsDefaultsMigration {

    /** Raise this, and add to [upgrade], when a default changes for everyone. */
    const val REVISION = 1

    fun needsUpgrade(storedRevision: Int): Boolean = storedRevision < REVISION

    fun upgrade(current: AppSettings): AppSettings = current.copy(
        autoDownloadUpdates = true,
        autoInstallUpdates = true,
        autoInstallNewApps = true,
        requiresCharging = false
    )
}
