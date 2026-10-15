package dev.wystore.ui.github

import dev.wystore.ui.github.ReleaseNotes.Block
import dev.wystore.ui.github.ReleaseNotes.Span
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Release cards read "## Version differences" with the hashes; release pages were raw Markdown. */
class ReleaseNotesTest {

    @Test
    fun `a card shows the words of the notes without the markup`() {
        val notes = """
            ## Version differences
            ### Android (arm64)
            * **Fixed** a crash on `start`
            - See [the docs](https://example.com/docs) for _details_
            ![screenshot](https://example.com/a.png)
        """.trimIndent()
        assertEquals(
            "Version differences\nAndroid (arm64)\n• Fixed a crash on start\n• See the docs for details",
            ReleaseNotes.plainText(notes)
        )
    }

    @Test
    fun `headings, items and code are told apart`() {
        val blocks = ReleaseNotes.parse(
            """
            # Title
            Some text
            1. first
              - nested
            > quoted
            ```
            ./gradlew build
            ```
            """.trimIndent()
        )
        assertEquals(Block.Heading(1, listOf(Span("Title"))), blocks[0])
        assertEquals(Block.Paragraph(listOf(Span("Some text"))), blocks[1])
        assertEquals(Block.Item("1.", 0, listOf(Span("first"))), blocks[2])
        assertEquals(Block.Item("•", 1, listOf(Span("nested"))), blocks[3])
        assertEquals(Block.Quote(listOf(Span("quoted"))), blocks[4])
        assertEquals(Block.Code("./gradlew build"), blocks[5])
        assertEquals(6, blocks.size)
    }

    @Test
    fun `emphasis and links become styled spans`() {
        assertEquals(
            listOf(
                Span("a "),
                Span("bold", bold = true),
                Span(" and "),
                Span("italic", italic = true),
                Span(" "),
                Span("link", url = "https://x.org"),
                Span(" "),
                Span("code", code = true)
            ),
            ReleaseNotes.inline("a **bold** and *italic* [link](https://x.org) `code`")
        )
    }

    @Test
    fun `underscores inside words and lone stars are left alone`() {
        assertEquals("app_release_v2.apk 2 * 3", ReleaseNotes.plainText("app_release_v2.apk 2 * 3"))
    }

    @Test
    fun `HTML, comments, escapes and table rules are cleaned up`() {
        val notes = """
            <!-- hidden -->
            <details><summary>Changes</summary>
            | File | Size |
            |------|-----:|
            | app.apk | 10 MB |
            \*not emphasis\*
            </details>
        """.trimIndent()
        assertEquals(
            "Changes\nFile · Size\napp.apk · 10 MB\n*not emphasis*",
            ReleaseNotes.plainText(notes)
        )
    }

    @Test
    fun `bare and angle-bracket links are recognised`() {
        val spans = ReleaseNotes.inline("Full changelog: https://github.com/o/r/compare/v1...v2. See <https://x.org>")
        assertTrue(spans.any { it.url == "https://github.com/o/r/compare/v1...v2" })
        assertTrue(spans.any { it.url == "https://x.org" && it.text == "https://x.org" })
    }

    @Test
    fun `notes with nothing but markup leave nothing behind`() {
        assertEquals("", ReleaseNotes.plainText("---\n![img](a.png)\n<br>\n"))
    }
}
