package dev.wystore.updates

import dev.wystore.data.InstalledApp
import dev.wystore.data.PendingUpdate

object InstalledUpdateMatcher {
    fun matches(pending: PendingUpdate, installed: InstalledApp?): Boolean =
        installed != null &&
            installed.packageName == pending.packageName &&
            installed.versionCode >= pending.versionCode &&
            installed.signingDigests == pending.signingDigests
}
