package dev.wystore.updates

import dev.wystore.data.InstalledApp
import dev.wystore.data.PendingUpdate

object PendingUpdateCatalog {
    fun upsert(current: List<PendingUpdate>, incoming: PendingUpdate): List<PendingUpdate> {
        val existing = current.firstOrNull { it.packageName == incoming.packageName }
        if (existing != null && existing.versionCode > incoming.versionCode) return current
        return current.filterNot { it.packageName == incoming.packageName } + incoming
    }

    fun confirmedPackages(current: List<PendingUpdate>, installed: List<InstalledApp>): Set<String> {
        val installedByPackage = installed.associateBy { it.packageName }
        return current.filter { pending ->
            InstalledUpdateMatcher.matches(pending, installedByPackage[pending.packageName])
        }.mapTo(mutableSetOf()) { it.packageName }
    }
}
