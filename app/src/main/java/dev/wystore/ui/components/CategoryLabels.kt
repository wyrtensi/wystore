package dev.wystore.ui.components

import dev.wystore.R

/**
 * Turns the source's category identifiers into words.
 *
 * The catalogue tags every app with its own slugs - "adsAndServices", "foodanddrink" - and the app
 * page printed them verbatim, so a Russian card carried two English identifiers under it. Known
 * slugs get the name the source itself shows in its catalogue; an unknown one is split into words
 * rather than hidden, because a new section is still information.
 */
object CategoryLabels {
    private val known = mapOf(
        // The catalogue's own "everything" section; without it the English list of sections had
        // one tile left in Russian.
        "all" to R.string.home_all_apps,
        "adsandservices" to R.string.category_adsandservices,
        "books" to R.string.category_books,
        "business" to R.string.category_business,
        "education" to R.string.category_education,
        "entertainment" to R.string.category_entertainment,
        "finance" to R.string.category_finance,
        "foodanddrink" to R.string.category_foodanddrink,
        "gambling" to R.string.category_gambling,
        "games" to R.string.category_games,
        "health" to R.string.category_health,
        "lifestyle" to R.string.category_lifestyle,
        "news" to R.string.category_news,
        "parenting" to R.string.category_parenting,
        "pets" to R.string.category_pets,
        "purchases" to R.string.category_purchases,
        "social" to R.string.category_social,
        "sport" to R.string.category_sport,
        "state" to R.string.category_state,
        "tools" to R.string.category_tools,
        "transport" to R.string.category_transport,
        "travelling" to R.string.category_travelling,
        // The GitHub catalogue's own sections; see GitHubCategories.
        "vpn" to R.string.category_vpn,
        "proxy" to R.string.category_proxy,
        "unblock" to R.string.category_unblock,
        "network" to R.string.category_network,
        "privacy" to R.string.category_privacy,
        "messengers" to R.string.category_messengers,
        "media" to R.string.category_media,
        "music" to R.string.category_music,
        "browsers" to R.string.category_browsers,
        "stores" to R.string.category_stores,
        "files" to R.string.category_files,
        "backup" to R.string.category_backup,
        "photos" to R.string.category_photos,
        "security" to R.string.category_security,
        "utilities" to R.string.category_utilities,
        "keyboards" to R.string.category_keyboards,
        "torrents" to R.string.category_torrents,
        "reading" to R.string.category_reading,
        "weather" to R.string.category_weather,
        "learning" to R.string.category_learning
    )

    /**
     * The string resource naming [slug], or null when the source invented a new one. Also takes a
     * section name as RuStore's web cards print it, which catalogue pages cached before the cards
     * were read as slugs still hold.
     */
    fun stringRes(slug: String): Int? =
        known[slug.trim().lowercase()] ?: dev.wystore.data.RuStoreCategoryNames.slugOf(slug)?.let { known[it] }

    /**
     * The name to show for a whole section, rather than for a tag under a card.
     *
     * A section Wy Store assembles itself carries its own resource. A section read from the source
     * arrives named in Russian, which is what the catalogue publishes - but these are the same
     * slugs this table already names, so the rail follows the interface language too. A section
     * nobody has a name for keeps what the catalogue published.
     */
    fun titleRes(category: dev.wystore.data.StoreCategory): Int? =
        category.titleRes ?: stringRes(category.slug)

    /**
     * "adsAndServices" -> "Ads and services", for a slug this table has never seen.
     *
     * Only for text shaped like a slug. The same pill also carries labels that were written for
     * people - "GitHub", "GPL-3.0", "Обход блокировок" - and splitting those on case turned them
     * into "Git hub" and "G p l-3.0".
     */
    fun humanize(slug: String): String {
        val trimmed = slug.trim()
        if (!SLUG.matches(trimmed)) return trimmed
        val spaced = buildString {
            trimmed.forEachIndexed { index, char ->
                if (index > 0 && char.isUpperCase()) append(' ')
                append(char)
            }
        }
        return spaced.first().uppercaseChar() + spaced.drop(1).lowercase()
    }

    /** Lower-case word, then optional capitalised words joined to it: the shape the source uses. */
    private val SLUG = Regex("[a-z]+([A-Z][a-z0-9]*)*")
}
