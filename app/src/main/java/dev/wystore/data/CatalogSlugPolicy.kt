package dev.wystore.data

/**
 * Catalog slugs come out of source HTML and are pasted straight into a request path, so they are
 * validated the same way package names are: only the shape the source actually uses is accepted,
 * which keeps traversal segments and absolute URLs out of the route.
 */
object CatalogSlugPolicy {
    private val ALLOWED = Regex("[a-z0-9]+(?:[-_][a-z0-9]+)*")
    private const val MAX_LENGTH = 64

    fun isValid(slug: String): Boolean {
        if (slug.isEmpty()) return true // the landing selection has no section of its own
        return slug.length <= MAX_LENGTH && ALLOWED.matches(slug)
    }

    fun requireValid(slug: String): String {
        val trimmed = slug.trim().trim('/')
        if (!isValid(trimmed)) {
            throw SourceFormatException(SourceError.INVALID_SECTION, "Rejected catalog slug: $slug")
        }
        return trimmed
    }
}
