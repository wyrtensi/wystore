package dev.wystore.data

/** The page an app opens on: its catalogue page, or the store-style page of its repository. */
sealed interface AppPage {
    data class Store(val packageName: String) : AppPage

    data class GitHub(val entry: GitHubCatalogEntry) : AppPage
}

/**
 * Which page a row opens, and which release a GitHub app's "what's new" belongs to.
 *
 * The phone opened a GitHub app by setting the repository whose releases the GitHub tab lists,
 * without going to that tab, so a tap on such a row in Updates or My apps did nothing that could
 * be seen. The TV built the page's entry itself; both now come here.
 */
object AppPages {

    /**
     * @param githubRepository the repository a queue row or a download names. A GitHub row's
     * package name is a placeholder until the file is verified, so the repository decides.
     * @param managed the app's record, for a row that only knows the package.
     */
    fun resolve(
        packageName: String,
        label: String,
        githubRepository: GitHubRepository? = null,
        managed: ManagedApp? = null,
        userRepositories: List<GitHubRepository> = emptyList()
    ): AppPage {
        val repository = githubRepository
            ?: managed?.githubRepository?.takeIf { managed.source == ManagedSource.GITHUB }
            ?: return AppPage.Store(packageName)
        return AppPage.GitHub(entryFor(repository, label, userRepositories))
    }

    /**
     * The catalogue's entry for [repository], or one made from the record: a repository whose
     * entry is gone from the list still has a page.
     */
    fun entryFor(
        repository: GitHubRepository,
        label: String,
        userRepositories: List<GitHubRepository> = emptyList()
    ): GitHubCatalogEntry {
        val key = repository.displayName.lowercase()
        return GitHubCatalog.entries(userRepositories).firstOrNull { it.repository.displayName.lowercase() == key }
            ?: GitHubCatalogEntry(
                repository = repository,
                title = label.ifBlank { repository.name },
                publisher = repository.owner,
                summary = ""
            )
    }

    /**
     * The release whose notes answer "what's new": the one already downloaded when there is one,
     * otherwise the one an update would install.
     */
    fun releaseForNotes(
        releases: List<GitHubRelease>,
        downloadedReleaseId: Long? = null,
        assetPattern: Regex? = null
    ): GitHubRelease? =
        downloadedReleaseId?.let { id -> releases.firstOrNull { it.id == id } }
            ?: GitHubReleasePolicy.selectRelease(releases, assetPattern)
            ?: releases.firstOrNull()

    /** A release tag as a version: "v2.16.1" under the "Version" label read "Version v2.16.1". */
    fun versionOfTag(tag: String): String = tag.trim().replaceFirst(TAG_PREFIX, "")

    private val TAG_PREFIX = Regex("^[vV]\\.?(?=\\d)")
}
