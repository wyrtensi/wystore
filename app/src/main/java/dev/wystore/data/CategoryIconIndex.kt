package dev.wystore.data

/**
 * The source's icon for a category, found by the name an app gives its category.
 *
 * The TV groups its catalogue by each app's own category, which arrives as the section's slug
 * ("entertainment") or as a game genre ("Головоломки") - not as one of the source's sections with
 * its icon. The phone's tiles carry that icon as a watermark; the TV's had none. The index answers
 * by slug and by title, ignoring case and spacing; a genre has no section and so no icon.
 */
object CategoryIconIndex {

    fun of(categories: List<StoreCategory>): Map<String, String> = buildMap {
        categories.forEach { category ->
            val icon = category.iconUrl?.takeIf { it.isNotBlank() } ?: return@forEach
            key(category.slug)?.let { putIfAbsent(it, icon) }
            key(category.title)?.let { putIfAbsent(it, icon) }
        }
    }

    fun lookup(index: Map<String, String>, name: String?): String? = key(name)?.let(index::get)

    private fun key(name: String?): String? =
        name?.trim()?.lowercase()?.replace(WHITESPACE, " ")?.takeIf { it.isNotEmpty() }

    private val WHITESPACE = Regex("""\s+""")
}
