package dev.wystore.data

/**
 * Which of the two catalogues a search looks in.
 *
 * The screen has always searched both and said so, but the two are not alike: RuStore is a request
 * over the network with a button to start it, and the GitHub list is bundled with the app and
 * needs nothing. Someone looking for an open-source client had to send a query to RuStore to see
 * it, and someone looking for a bank had a section of GitHub repositories in the way.
 */
enum class SearchSources {
    ALL,
    RUSTORE,
    GITHUB;

    val includesRuStore: Boolean get() = this != GITHUB
    val includesGitHub: Boolean get() = this != RUSTORE
}
