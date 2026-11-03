package dev.wystore.data

/** Repositories served through dedicated Wy Store features rather than ordinary GitHub sources. */
object ReservedGitHubRepository {
    fun error(repository: GitHubRepository): SourceError? {
        if (!repository.owner.equals("wyrtensi", ignoreCase = true)) return null
        val name = repository.name.replace(Regex("(?i)\\.git$"), "")
        return when {
            name.equals("wystore-library", ignoreCase = true) -> SourceError.GITHUB_RESERVED_LIBRARY
            name.equals("wystore", ignoreCase = true) -> SourceError.GITHUB_RESERVED_SELF
            else -> null
        }
    }

    fun requireAllowedManaged(apps: List<ManagedApp>, selfPackage: String) {
        apps.filterNot { it.packageName == selfPackage }.forEach { app ->
            app.githubRepository?.let(::requireAllowed)
            app.knownGithubRepository?.let(::requireAllowed)
        }
    }

    fun requireAllowed(repository: GitHubRepository) {
        error(repository)?.let { throw SourceFormatException(it, "Repository uses a dedicated Wy Store source") }
    }
}
