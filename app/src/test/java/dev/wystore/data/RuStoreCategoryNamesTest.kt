package dev.wystore.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuStoreCategoryNamesTest {
    @Test
    fun cardSectionsBecomeSlugs() {
        assertEquals(listOf("entertainment"), RuStoreCategoryNames.slugsOf("Развлечения"))
        assertEquals(listOf("finance", "business"), RuStoreCategoryNames.slugsOf("Финансы·Бизнес-сервисы"))
        assertEquals(listOf("transport"), RuStoreCategoryNames.slugsOf(" Транспорт и навигация "))
    }

    @Test
    fun gameGenresAreKeptAsWritten() {
        assertEquals(listOf("Ролевые", "Стратегии"), RuStoreCategoryNames.slugsOf("Ролевые·Стратегии"))
        assertEquals(listOf("tools", "Ролевые"), RuStoreCategoryNames.slugsOf("Полезные инструменты·Ролевые"))
        assertNull(RuStoreCategoryNames.slugOf("Ролевые"))
    }

    @Test
    fun blankLabelGivesNoSections() {
        assertEquals(emptyList<String>(), RuStoreCategoryNames.slugsOf(" · "))
    }

    @Test
    fun searchCardsCarrySlugs() {
        val page = RustoreHtmlParser.parseSearchPage(
            """
            <html><body>
              <h1>По запросу «Банк» найдено 1 приложений</h1>
              <a data-testid="app-card" href="/catalog/app/com.example.bank">
                <p>Пример Банк</p><p>Финансы·Бизнес-сервисы</p>
              </a>
            </body></html>
            """.trimIndent(),
            page = 1
        )

        assertEquals(listOf("finance", "business"), page.apps.single().categories)
    }
}
