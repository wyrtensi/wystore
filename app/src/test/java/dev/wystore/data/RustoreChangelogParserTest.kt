package dev.wystore.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parsed from markup captured verbatim from a real rustore.ru app page. The class names there are
 * build-generated hashes, so the parser keys off the heading and the visually-hidden field labels;
 * these tests are what would catch that structure changing.
 */
class RustoreChangelogParserTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name")) {
            "Missing fixture: $name"
        }.bufferedReader().use { it.readText() }

    @Test
    fun theWhatsNewBlockYieldsVersionDateAndNotes() {
        val changelog = RustoreHtmlParser.parseChangelog(fixture("rustore_app_changelog.html"))

        assertNotNull(changelog)
        assertEquals("12.10.1", changelog!!.versionName)
        assertEquals("25 авг 2026", changelog.publishedAt)
        assertTrue("notes must be the release text", changelog.notes.startsWith("Расширенное форматирование"))
        assertTrue("notes should be substantial", changelog.notes.length > 200)
        // The labels are screen-reader captions, not part of the value.
        assertTrue(changelog.versionName?.contains("Версия") != true)
        assertTrue(changelog.publishedAt?.contains("Дата") != true)
    }

    @Test
    fun aPageWithoutTheBlockYieldsNothingRatherThanFailing() {
        assertNull(RustoreHtmlParser.parseChangelog("<html><body><h2>Отзывы</h2></body></html>"))
        assertNull(RustoreHtmlParser.parseChangelog("<html><body></body></html>"))
    }

    @Test
    fun aBlockWithNoNotesIsTreatedAsAbsent() {
        val markup = """
            <html><body><div>
              <h2>Что нового</h2>
              <p><span class="visually-hidden">Версия: </span>1.2.3</p>
            </div></body></html>
        """.trimIndent()

        assertNull("a heading alone is not a changelog", RustoreHtmlParser.parseChangelog(markup))
    }

    @Test
    fun notesSurviveWithoutVersionOrDateLabels() {
        val markup = """
            <html><body><div>
              <h2>Что нового</h2>
              <p>Исправлены ошибки и улучшена стабильность.</p>
            </div></body></html>
        """.trimIndent()

        val changelog = RustoreHtmlParser.parseChangelog(markup)

        assertNotNull(changelog)
        assertNull(changelog!!.versionName)
        assertNull(changelog.publishedAt)
        assertEquals("Исправлены ошибки и улучшена стабильность.", changelog.notes)
    }
}
