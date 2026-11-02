package dev.wystore.data

/** Removal consent is tied to one visible operation and the installed package the user saw. */
data class SourceReplacementConsent(val operation: Long, val revision: Long, val installed: InstalledApp) {
    fun isCurrent(currentOperation: Long?, currentRevision: Long, current: InstalledApp?): Boolean =
        operation == currentOperation && revision == currentRevision && current != null &&
            installed.packageName == current.packageName && installed.versionCode == current.versionCode &&
            installed.lastUpdateTime == current.lastUpdateTime && installed.signingDigests == current.signingDigests
}
