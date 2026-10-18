package dev.wystore.ui.library

import dev.wystore.data.InstalledApp

/**
 * How My apps splits the list.
 *
 * With 150 apps installed, the ones not accepted yet could only be found by scrolling the whole
 * list and reading each badge. Under "All" they now have a group of their own beneath the accepted
 * ones; every other filter already says which apps it holds, so it stays one list. While several
 * apps are being chosen to accept, only those that can be accepted are shown.
 */
object LibraryGrouping {

    const val ADOPTED = "adopted"
    const val NOT_ADOPTED = "not_adopted"
    private const val PLAIN = "plain"

    data class Group(val key: String, val titled: Boolean, val apps: List<InstalledApp>)

    /** [apps] in the screen's order; the order is kept inside each group. */
    fun groups(
        apps: List<InstalledApp>,
        adopted: Set<String>,
        sort: LibrarySort,
        selecting: Boolean
    ): List<Group> {
        val (accepted, notAccepted) = apps.partition { it.packageName in adopted }
        return when {
            selecting -> listOf(Group(NOT_ADOPTED, titled = true, apps = notAccepted))
            sort == LibrarySort.ALL -> listOf(
                Group(ADOPTED, titled = true, apps = accepted),
                Group(NOT_ADOPTED, titled = true, apps = notAccepted)
            ).filter { it.apps.isNotEmpty() }
            sort == LibrarySort.NOT_ADOPTED -> listOf(Group(NOT_ADOPTED, titled = true, apps = notAccepted))
                .filter { it.apps.isNotEmpty() }
            else -> listOf(Group(PLAIN, titled = false, apps = apps)).filter { it.apps.isNotEmpty() }
        }
    }

    /** Why the list came out empty, which decides what the screen says. */
    enum class EmptyReason {
        /** The search found nothing: clearing it is the answer. */
        SEARCH,

        /** "Not accepted" with nothing left in it: every app is already looked after. */
        ALL_ACCEPTED,

        /** Another filter holds no apps; the search is not the reason. */
        FILTER
    }

    /**
     * An empty "Not accepted" said "nothing matches, clear the search or pick another filter" with
     * the search empty - when the news was that every app had been accepted.
     */
    fun emptyReason(query: String, sort: LibrarySort): EmptyReason = when {
        query.isNotBlank() -> EmptyReason.SEARCH
        sort == LibrarySort.NOT_ADOPTED -> EmptyReason.ALL_ACCEPTED
        else -> EmptyReason.FILTER
    }
}
