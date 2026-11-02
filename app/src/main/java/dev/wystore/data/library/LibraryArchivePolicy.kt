package dev.wystore.data.library

import dev.wystore.data.ArchiveIdentity

/** The listing's text version cannot replace the native identity of the selected delivered set. */
object LibraryArchivePolicy {
    fun matches(expected: ResolvedLibraryVersion, actual: ArchiveIdentity, fileCount: Int): Boolean =
        actual.packageName == expected.packageName && actual.versionCode == expected.versionCode &&
            fileCount == expected.members.size
}
