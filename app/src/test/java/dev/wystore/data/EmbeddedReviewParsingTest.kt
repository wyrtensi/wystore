package dev.wystore.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reviews page renders from a payload embedded for its own use, and that payload carries what
 * the schema.org block beside it does not: the developer's reply, the votes, whether a review was
 * edited. The escaping below is the page's own - the payload is a JSON string inside a script, so
 * every quote in it arrives escaped once.
 */
class EmbeddedReviewParsingTest {

    private fun page(payload: String): String =
        """<html><body><script>self.__next_f.push([1,"$payload"])</script></body></html>"""

    @Test
    fun aReviewCarriesItsVotesAndTheDeveloperAnswer() {
        val html = page(
            """\"reviews\":[{\"id\":1,\"rating\":1,\"firstName\":\"Игорь\",""" +
                """\"comment\":\"Не работает\",\"commentDate\":\"2026-09-09T11:19:29.436Z\",""" +
                """\"editedAt\":\"2026-09-09T11:21:02.932Z\",\"likesCount\":7,\"dislikesCount\":2,""" +
                """\"devResponse\":\"Напишите нам\",\"devResponseDate\":\"2026-09-09T12:00:00.000Z\"}]"""
        )

        val reviews = RustoreHtmlParser.parseReviews(html)

        assertEquals(1, reviews.size)
        val review = reviews.single()
        assertEquals("Игорь", review.author)
        assertEquals(1, review.rating)
        assertEquals("Не работает", review.text)
        assertEquals(7, review.likes)
        assertEquals(2, review.dislikes)
        assertEquals("Напишите нам", review.developerResponse)
        assertTrue(review.edited)
    }

    /** A review nobody answered says so by having nothing, not by carrying an empty answer. */
    @Test
    fun anUnansweredReviewHasNoReply() {
        val html = page(
            """\"reviews\":[{\"id\":2,\"rating\":5,\"firstName\":\"Аня\",\"comment\":\"Отлично\",""" +
                """\"commentDate\":\"2026-09-01T10:00:00.000Z\",\"likesCount\":0,""" +
                """\"dislikesCount\":0,\"devResponse\":null}]"""
        )

        val review = RustoreHtmlParser.parseReviews(html).single()

        assertNull(review.developerResponse)
        assertEquals(false, review.edited)
    }

    /**
     * Brackets inside a review must not end the array early - a review is free to contain one, and
     * the scan has to know the difference between a bracket in text and a bracket in structure.
     */
    @Test
    fun aBracketInsideAReviewDoesNotEndTheList() {
        val html = page(
            """\"reviews\":[{\"id\":3,\"rating\":4,\"firstName\":\"К\",""" +
                """\"comment\":\"Список [1] и ещё ]\",\"commentDate\":\"2026-09-01T10:00:00.000Z\"},""" +
                """{\"id\":4,\"rating\":2,\"firstName\":\"Л\",\"comment\":\"Второй\",""" +
                """\"commentDate\":\"2026-09-02T10:00:00.000Z\"}]"""
        )

        val reviews = RustoreHtmlParser.parseReviews(html)

        assertEquals(2, reviews.size)
        assertEquals("Список [1] и ещё ]", reviews.first().text)
    }

    /**
     * The payload is private to the page. The day its shape changes the reviews fall back to the
     * published schema.org block rather than disappearing.
     */
    @Test
    fun aPageWithoutThePayloadFallsBackToTheSchemaBlock() {
        val html = """
            <html><body><script type="application/ld+json">
            {"@type":"Product","review":[{"@type":"Review","reviewBody":"Из схемы",
            "author":{"name":"Схема"},"reviewRating":{"ratingValue":3}}]}
            </script></body></html>
        """.trimIndent()

        val review = RustoreHtmlParser.parseReviews(html).single()

        assertEquals("Из схемы", review.text)
        assertEquals(3, review.rating)
        assertNull(review.developerResponse)
    }
}
