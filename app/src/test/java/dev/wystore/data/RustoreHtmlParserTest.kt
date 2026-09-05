package dev.wystore.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RustoreHtmlParserTest {
    @Test
    fun parsesServerRenderedSearchCards() {
        val page = RustoreHtmlParser.parseSearchPage(
            """
            <html><body>
              <h1>По запросу «Банк» найдено 2 приложений</h1>
              <a data-testid="app-card" href="/catalog/app/com.example.bank">
                <img src="https://static.rustore.ru/icon.png" />
                <p>Пример Банк</p><p>Финансы</p><span data-testid="rating">4,7</span>
              </a>
            </body></html>
            """.trimIndent(),
            page = 1
        )

        assertEquals(2, page.total)
        assertEquals(1, page.apps.size)
        assertEquals("com.example.bank", page.apps.single().packageName)
        assertEquals("Пример Банк", page.apps.single().name)
        assertEquals(4.7, page.apps.single().rating ?: 0.0, 0.001)
    }

    @Test
    fun rejectsUnknownSearchMarkup() {
        assertThrows(SourceFormatException::class.java) {
            RustoreHtmlParser.parseSearchPage("<html><body>неизвестный формат</body></html>", 1)
        }
    }

    @Test
    fun parsesReviewPreviewsFromJsonLd() {
        val reviews = RustoreHtmlParser.parseReviewPreviews(
            """
            <script type="application/ld+json">
            {"@type":"SoftwareApplication","review":[
              {"author":{"name":"Алексей"},"datePublished":"2026-07-12T06:58:10Z","reviewBody":"Всё работает.","reviewRating":{"ratingValue":5}},
              {"author":{"name":"Маша"},"reviewBody":"Не запускается","reviewRating":{"ratingValue":1}}
            ]}
            </script>
            """.trimIndent()
        )

        assertEquals(2, reviews.size)
        assertEquals("Алексей", reviews.first().author)
        assertEquals(5, reviews.first().rating)
        assertEquals("Всё работает.", reviews.first().text)
    }
}
