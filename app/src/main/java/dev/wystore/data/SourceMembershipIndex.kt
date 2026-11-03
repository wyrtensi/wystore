package dev.wystore.data

import dev.wystore.data.library.LibraryCard

/** Membership is built once per source snapshot instead of scanning each catalogue per card. */
class SourceMembershipIndex(
    libraryEnabled: Boolean,
    githubEnabled: Boolean,
    libraryCards: List<LibraryCard>,
    knownRuStorePackages: Set<String>,
    managed: List<ManagedApp>,
    githubPackages: List<String>
) {
    private val library = if (libraryEnabled) libraryCards.mapTo(hashSetOf()) { it.packageName } else emptySet()
    private val rustore = knownRuStorePackages.toSet()
    private val github = if (githubEnabled) githubPackages.toMutableSet().apply {
        managed.forEach {
            if (it.githubRepository != null || it.knownGithubRepository != null) add(it.packageName)
        }
    } else emptySet()

    fun sourcesFor(packageName: String, existing: Set<ManagedSource>): Set<ManagedSource> = existing.toMutableSet().apply {
        if (packageName in library) add(ManagedSource.WY_STORE)
        if (packageName in rustore) add(ManagedSource.RUSTORE)
        if (packageName in github) add(ManagedSource.GITHUB)
    }
}
