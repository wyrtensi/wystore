package dev.wystore.updates

import dev.wystore.data.InstalledApp
import dev.wystore.data.PendingUpdate

object InstalledUpdateMatcher {
    fun matches(pending: PendingUpdate, installed: InstalledApp?): Boolean =
        installed != null &&
            installed.packageName == pending.packageName &&
            installed.signingDigests == pending.signingDigests &&
            (installed.versionCode > pending.versionCode ||
                // The same version counts only when it was installed after this download began.
                // "Take over" and "Reinstall" fetch the version already on the phone, and reading
                // that as installed deleted the file before it was installed.
                (installed.versionCode == pending.versionCode && installed.lastUpdateTime >= pending.downloadedAt))
}
