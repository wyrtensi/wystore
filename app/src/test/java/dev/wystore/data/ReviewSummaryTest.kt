package dev.wystore.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewSummaryTest {

    private fun review(rating: Int?, text: String = "text") =
        StoreReview(author = "a", publishedAt = null, rating = rating, text = text)

    /** Every star, including the ones nobody gave: a missing row reads as a missing bar. */
    @Test
    fun theBreakdownCoversAllFiveStarsInOrder() {
        val distribution = ReviewSummary.distribution(
            listOf(review(5), review(5), review(3), review(1))
        )

        assertEquals(listOf(5 to 2, 4 to 0, 3 to 1, 2 to 0, 1 to 1), distribution)
    }

    @Test
    fun theAverageIsOfTheRatingsThatAreThere() {
        assertEquals(3.0, ReviewSummary.average(listOf(review(5), review(1)))!!, 0.001)
        assertNull(ReviewSummary.average(listOf(review(null))))
        assertNull(ReviewSummary.average(emptyList()))
    }

    /** A review with no rating belongs to no star, so a filter must not claim it. */
    @Test
    fun anUnratedReviewIsOnlyInTheUnfilteredList() {
        val reviews = listOf(review(5, "rated"), review(null, "unrated"))

        assertEquals(2, ReviewSummary.filter(reviews, null).size)
        assertEquals(listOf("rated"), ReviewSummary.filter(reviews, 5).map { it.text })
        assertEquals(emptyList<String>(), ReviewSummary.filter(reviews, 1).map { it.text })
    }

    /**
     * The card embeds a handful of reviews and the rest are fetched on request. Answering "no
     * one-star reviews" from the handful would be answering from a sample far too small to say it.
     */
    @Test
    fun choosingAStarAsksForTheRestFirst() {
        assertTrue(ReviewSummary.needsEveryReview(stars = 1, canLoadMore = true))
        assertFalse(ReviewSummary.needsEveryReview(stars = 1, canLoadMore = false))
        assertFalse(ReviewSummary.needsEveryReview(stars = null, canLoadMore = true))
    }
}
