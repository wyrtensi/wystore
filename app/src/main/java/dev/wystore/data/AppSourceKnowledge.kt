package dev.wystore.data

object AppSourceKnowledge {
    fun remember(current: ManagedApp?, chosen: GitHubRepository?): GitHubRepository? =
        chosen ?: current?.githubRepository ?: current?.knownGithubRepository

    fun packageFor(repository: GitHubRepository, managed: List<ManagedApp>): String? = managed.filter {
        it.githubRepository == repository || it.knownGithubRepository == repository
    }.map { it.packageName }.distinct().singleOrNull()
}
