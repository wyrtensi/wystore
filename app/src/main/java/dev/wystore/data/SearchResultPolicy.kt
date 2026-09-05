package dev.wystore.data

/**
 * Ordering and merging rules for search results, kept out of the ViewModel so they can be tested
 * without an Android runtime.
 */
object SearchResultPolicy {

    /**
     * Moves an exact package-name match to the front.
     *
     * The source ranks by relevance to the typed text, so searching for a package id can leave the
     * package itself below similarly named apps. Only queries that look like a package id are
     * considered, and only an exact (case-insensitive) match is promoted.
     */
    fun promoteExactPackage(page: SearchPage, query: String): SearchPage {
        val trimmed = query.trim()
        if (!trimmed.contains('.') || page.apps.isEmpty()) return page
        val index = page.apps.indexOfFirst { it.packageName.equals(trimmed, ignoreCase = true) }
        if (index <= 0) return page
        val reordered = page.apps.toMutableList().apply { add(0, removeAt(index)) }
        return page.copy(apps = reordered)
    }

    /**
     * Appends a newly fetched page, keeping the order and dropping repeats. Sources can return the
     * same app on consecutive pages while the catalogue shifts under paging.
     */
    fun appendPage(current: SearchPage, next: SearchPage): SearchPage = next.copy(
        apps = (current.apps + next.apps).distinctBy { it.packageName },
        total = next.total ?: current.total
    )

    /** True when the source has nothing further to give for this query. */
    fun isExhausted(page: SearchPage): Boolean {
        val total = page.total ?: return false
        return page.apps.size >= total
    }
}
