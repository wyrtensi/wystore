package dev.wystore.data

/**
 * What a pile of reviews says at a glance, and how to cut it.
 *
 * The card used to show reviews as one undifferentiated list, newest first, which answers the wrong
 * question: someone reading reviews before installing is usually looking for the bad ones, and
 * finding them meant scrolling past every five-star note in between.
 *
 * Everything here is computed from the reviews actually in hand. The source publishes its own
 * average over all of them, and that is what the app's rating shows; these numbers describe the
 * sample on screen and are labelled as such rather than presented as the app's score.
 */
object ReviewSummary {

    /** Counts per star, five down to one, including the stars nobody gave. */
    fun distribution(reviews: List<StoreReview>): List<Pair<Int, Int>> {
        val counted = reviews.mapNotNull { it.rating }.groupingBy { it }.eachCount()
        return (5 downTo 1).map { stars -> stars to (counted[stars] ?: 0) }
    }

    /** The mean of the ratings present, or null when none of the reviews carries one. */
    fun average(reviews: List<StoreReview>): Double? {
        val ratings = reviews.mapNotNull { it.rating }
        if (ratings.isEmpty()) return null
        return ratings.sum().toDouble() / ratings.size
    }

    /**
     * Reviews with the chosen rating, or all of them when [stars] is null.
     *
     * A review without a rating is kept only in the unfiltered list: it cannot be claimed for a
     * star it never gave.
     */
    fun filter(reviews: List<StoreReview>, stars: Int?): List<StoreReview> =
        if (stars == null) reviews else reviews.filter { it.rating == stars }

    /**
     * Whether a filter can be trusted with what is loaded.
     *
     * The app's own page embeds a handful of reviews and the rest arrive from the source's review
     * page on request. Filtering the handful would answer "no one-star reviews" from a sample far
     * too small to say it, so choosing a star asks for the rest first.
     */
    fun needsEveryReview(stars: Int?, canLoadMore: Boolean): Boolean = stars != null && canLoadMore
}
