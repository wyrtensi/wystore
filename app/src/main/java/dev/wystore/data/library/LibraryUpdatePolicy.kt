package dev.wystore.data.library

object LibraryUpdatePolicy {
    fun isNewer(installedVersionCode: Long, available: ResolvedLibraryVersion): Boolean =
        available.versionCode > 0 && available.versionCode > installedVersionCode
}
