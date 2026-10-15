package dev.wystore.ui.github

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.em

/**
 * Release notes as GitHub writes them: Markdown, often with some HTML mixed in.
 *
 * They were shown as they came, so a release card read "## Version differences" with the hashes
 * and a release page was a wall of asterisks and brackets. This reads the parts release notes
 * actually use - headings, lists, quotes, code, emphasis, links, tables - and drops the rest
 * (images, HTML tags, comments). It is not a full CommonMark parser and does not try to be.
 */
object ReleaseNotes {

    sealed interface Block {
        data class Heading(val level: Int, val spans: List<Span>) : Block
        data class Paragraph(val spans: List<Span>) : Block
        data class Item(val marker: String, val depth: Int, val spans: List<Span>) : Block
        data class Quote(val spans: List<Span>) : Block
        data class Code(val text: String) : Block
    }

    data class Span(
        val text: String,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val code: Boolean = false,
        val strike: Boolean = false,
        val url: String? = null
    )

    fun parse(markdown: String): List<Block> {
        val lines = markdown
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace(HTML_COMMENT, "")
            .lines()
        val blocks = mutableListOf<Block>()
        val paragraph = mutableListOf<String>()
        fun flush() {
            if (paragraph.isNotEmpty()) {
                val spans = inline(paragraph.joinToString("\n"))
                if (spans.isNotEmpty()) blocks += Block.Paragraph(spans)
                paragraph.clear()
            }
        }
        var index = 0
        while (index < lines.size) {
            val raw = lines[index]
            val line = raw.trimEnd()
            val trimmed = line.trimStart()
            index++
            val fence = FENCE.matchEntire(trimmed)
            when {
                fence != null -> {
                    flush()
                    val marker = fence.groupValues[1]
                    val code = mutableListOf<String>()
                    while (index < lines.size && !lines[index].trimStart().startsWith(marker)) {
                        code += lines[index].trimEnd()
                        index++
                    }
                    index++ // the closing fence, or past the end
                    if (code.any { it.isNotBlank() }) blocks += Block.Code(code.joinToString("\n").trimEnd())
                }
                trimmed.isEmpty() -> flush()
                RULE.matches(trimmed) -> flush()
                TABLE_SEPARATOR.matches(trimmed) -> Unit
                HEADING.matchEntire(trimmed) != null -> {
                    flush()
                    val match = HEADING.matchEntire(trimmed)!!
                    val spans = inline(match.groupValues[2].trimEnd('#', ' '))
                    if (spans.isNotEmpty()) blocks += Block.Heading(match.groupValues[1].length, spans)
                }
                SETEXT.matches(trimmed) && paragraph.isNotEmpty() -> {
                    val title = paragraph.removeAt(paragraph.lastIndex)
                    flush()
                    val spans = inline(title)
                    if (spans.isNotEmpty()) blocks += Block.Heading(if (trimmed.startsWith("=")) 1 else 2, spans)
                }
                ITEM.matchEntire(line) != null -> {
                    flush()
                    val match = ITEM.matchEntire(line)!!
                    val indent = match.groupValues[1].replace("\t", "    ").length
                    val bullet = match.groupValues[2]
                    var text = match.groupValues[3]
                    val task = TASK.matchEntire(text)
                    val marker = when {
                        task != null -> if (task.groupValues[1].isBlank()) "☐" else "☑"
                        bullet.first().isDigit() -> bullet.dropLast(1) + "."
                        else -> "•"
                    }
                    if (task != null) text = task.groupValues[2]
                    val spans = inline(text)
                    if (spans.isNotEmpty()) blocks += Block.Item(marker, indent / 2, spans)
                }
                trimmed.startsWith(">") -> {
                    flush()
                    val quote = mutableListOf(trimmed.removePrefix(">").trimStart())
                    while (index < lines.size && lines[index].trimStart().startsWith(">")) {
                        quote += lines[index].trimStart().removePrefix(">").trimStart()
                        index++
                    }
                    val spans = inline(quote.filter { it.isNotBlank() }.joinToString("\n"))
                    if (spans.isNotEmpty()) blocks += Block.Quote(spans)
                }
                trimmed.startsWith("|") -> {
                    flush()
                    val cells = trimmed.trim('|').split("|").map { it.trim() }.filter { it.isNotEmpty() }
                    val spans = inline(cells.joinToString(" · "))
                    if (spans.isNotEmpty()) blocks += Block.Paragraph(spans)
                }
                else -> paragraph += trimmed
            }
        }
        flush()
        return blocks
    }

    /** One line or a few, for a card: the words of the notes without any of the markup. */
    fun plainText(markdown: String): String =
        parse(markdown).mapNotNull { block ->
            when (block) {
                is Block.Heading -> block.spans.text()
                is Block.Paragraph -> block.spans.text()
                is Block.Item -> "${block.marker} ${block.spans.text()}"
                is Block.Quote -> block.spans.text()
                // Code in notes is commands and logs; a card has no room to make sense of it.
                is Block.Code -> null
            }
        }
            .map { it.replace(WHITESPACE, " ").trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")

    /**
     * The notes for a page. Headings are bold and a little larger, lists keep their markers and
     * links are tappable when [clickableLinks] is set (a TV has nothing to tap them with).
     */
    fun annotated(markdown: String, linkColor: Color, codeBackground: Color, clickableLinks: Boolean = true): AnnotatedString =
        buildAnnotatedString {
            parse(markdown).forEachIndexed { index, block ->
                if (index > 0) append(if (block is Block.Heading) "\n\n" else "\n")
                when (block) {
                    is Block.Heading -> {
                        val size = when (block.level) { 1 -> 1.25.em; 2 -> 1.15.em; else -> 1.05.em }
                        pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = size))
                        appendSpans(block.spans, linkColor, codeBackground, clickableLinks)
                        pop()
                    }
                    is Block.Paragraph -> appendSpans(block.spans, linkColor, codeBackground, clickableLinks)
                    is Block.Item -> {
                        append("    ".repeat(block.depth))
                        append(block.marker)
                        append(" ")
                        appendSpans(block.spans, linkColor, codeBackground, clickableLinks)
                    }
                    is Block.Quote -> {
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        append("│ ")
                        appendSpans(block.spans, linkColor, codeBackground, clickableLinks)
                        pop()
                    }
                    is Block.Code -> {
                        pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground))
                        append(block.text)
                        pop()
                    }
                }
            }
        }

    private fun AnnotatedString.Builder.appendSpans(
        spans: List<Span>,
        linkColor: Color,
        codeBackground: Color,
        clickableLinks: Boolean
    ) {
        spans.forEach { span ->
            val style = SpanStyle(
                fontWeight = if (span.bold) FontWeight.Bold else null,
                fontStyle = if (span.italic) FontStyle.Italic else null,
                fontFamily = if (span.code) FontFamily.Monospace else null,
                background = if (span.code) codeBackground else Color.Unspecified,
                textDecoration = if (span.strike) TextDecoration.LineThrough else null
            )
            val url = span.url
            when {
                url != null && clickableLinks -> {
                    pushLink(
                        LinkAnnotation.Url(
                            url,
                            TextLinkStyles(style.merge(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)))
                        )
                    )
                    append(span.text)
                    pop()
                }
                url != null -> {
                    pushStyle(style.merge(SpanStyle(color = linkColor)))
                    append(span.text)
                    pop()
                }
                else -> {
                    pushStyle(style)
                    append(span.text)
                    pop()
                }
            }
        }
    }

    private fun List<Span>.text() = joinToString("") { it.text }

    /** Emphasis, code, links; images and HTML tags are dropped, escapes are honoured. */
    internal fun inline(source: String): List<Span> {
        val text = source
            .replace(IMAGE, "")
            .replace(BR_TAG, "\n")
            .replace(HTML_TAG, "")
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
        val spans = mutableListOf<Span>()
        val buffer = StringBuilder()
        var bold = false
        var italic = false
        var strike = false
        fun emit() {
            if (buffer.isNotEmpty()) {
                spans += Span(buffer.toString(), bold = bold, italic = italic, strike = strike)
                buffer.clear()
            }
        }
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '\\' && i + 1 < text.length && text[i + 1] in ESCAPABLE -> {
                    buffer.append(text[i + 1]); i += 2
                }
                c == '`' -> {
                    val run = text.countRun(i, '`')
                    val close = text.indexOf("`".repeat(run), i + run)
                    if (close < 0) {
                        buffer.append(text, i, i + run); i += run
                    } else {
                        emit()
                        val code = text.substring(i + run, close).trim()
                        if (code.isNotEmpty()) spans += Span(code, bold = bold, italic = italic, code = true)
                        i = close + run
                    }
                }
                c == '[' -> {
                    val link = LINK.matchAt(text, i)
                    if (link == null) {
                        buffer.append(c); i++
                    } else {
                        emit()
                        val label = link.groupValues[1].ifBlank { link.groupValues[2] }
                        inline(label).forEach { inner ->
                            spans += inner.copy(
                                bold = inner.bold || bold,
                                italic = inner.italic || italic,
                                strike = inner.strike || strike,
                                url = link.groupValues[2]
                            )
                        }
                        i = link.range.last + 1
                    }
                }
                c == '<' && AUTOLINK.matchAt(text, i) != null -> {
                    val link = AUTOLINK.matchAt(text, i)!!
                    emit()
                    spans += Span(link.groupValues[1], bold = bold, italic = italic, url = link.groupValues[1])
                    i = link.range.last + 1
                }
                c == 'h' && (i == 0 || !text[i - 1].isLetterOrDigit()) && BARE_URL.matchAt(text, i) != null -> {
                    val link = BARE_URL.matchAt(text, i)!!
                    val url = link.value.trimEnd('.', ',', ')', ';', ':', '!', '?')
                    emit()
                    spans += Span(url, bold = bold, italic = italic, url = url)
                    i += url.length
                }
                c == '~' && text.startsWith("~~", i) -> {
                    emit(); strike = !strike; i += 2
                }
                (c == '*' || c == '_') -> {
                    val run = text.countRun(i, c)
                    val before = text.getOrNull(i - 1)
                    val after = text.getOrNull(i + run)
                    // An underscore inside a word is part of it: file_name_v2, not emphasis.
                    val intraword = c == '_' && before?.isLetterOrDigit() == true && after?.isLetterOrDigit() == true
                    // A lone star between spaces is a multiplication or a footnote, not emphasis.
                    val loose = (before == null || before.isWhitespace()) && (after == null || after.isWhitespace())
                    if (intraword || loose) {
                        buffer.append(text, i, i + run)
                    } else {
                        emit()
                        if (run >= 2) bold = !bold
                        if (run % 2 == 1) italic = !italic
                    }
                    i += run
                }
                else -> {
                    buffer.append(c); i++
                }
            }
        }
        emit()
        // Lines that held only markup leave blank spans behind; so do unclosed markers.
        return spans.filter { it.text.isNotEmpty() }.trimBlankEdges()
    }

    private fun List<Span>.trimBlankEdges(): List<Span> {
        val first = indexOfFirst { it.text.isNotBlank() }
        if (first < 0) return emptyList()
        val last = indexOfLast { it.text.isNotBlank() }
        return subList(first, last + 1).mapIndexed { index, span ->
            when (index) {
                0 -> span.copy(text = span.text.trimStart())
                last - first -> span.copy(text = span.text.trimEnd())
                else -> span
            }
        }.let { trimmed ->
            if (trimmed.size == 1) listOf(trimmed[0].copy(text = trimmed[0].text.trim())) else trimmed
        }
    }

    private fun String.countRun(start: Int, char: Char): Int {
        var end = start
        while (end < length && this[end] == char) end++
        return end - start
    }

    private val HTML_COMMENT = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
    private val FENCE = Regex("(```+|~~~+).*")
    private val RULE = Regex("(\\*\\s*){3,}|(-\\s*){3,}|(_\\s*){3,}")
    private val SETEXT = Regex("=+|-+")
    private val TABLE_SEPARATOR = Regex("\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?")
    private val HEADING = Regex("(#{1,6})\\s+(.*)")
    private val ITEM = Regex("([ \\t]*)([-*+]|\\d{1,3}[.)])\\s+(.*)")
    private val TASK = Regex("\\[([ xX])]\\s+(.*)")
    private val IMAGE = Regex("!\\[[^\\]]*]\\([^)]*\\)")
    private val BR_TAG = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
    private val HTML_TAG = Regex("</?(?!https?://)[a-zA-Z][^>]*>")
    private val LINK = Regex("\\[([^\\]]*)]\\(\\s*<?([^)\\s>]+)>?(?:\\s+\"[^\"]*\")?\\s*\\)")
    private val AUTOLINK = Regex("<(https?://[^>\\s]+)>")
    private val BARE_URL = Regex("https?://[^\\s<>()\\[\\]]+")
    private val WHITESPACE = Regex("\\s+")
    private const val ESCAPABLE = "\\`*_{}[]()#+-.!|~<>"
}

/** Release notes drawn from their Markdown, with tappable links; [empty] when there are none. */
@Composable
fun ReleaseNotesText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    empty: String? = null
) {
    val colors = MaterialTheme.colorScheme
    val text = remember(markdown, colors.primary, colors.surfaceContainerHighest) {
        ReleaseNotes.annotated(markdown, colors.primary, colors.surfaceContainerHighest)
    }
    if (text.isEmpty() && empty != null) {
        Text(empty, modifier = modifier, style = style)
    } else {
        Text(text, modifier = modifier, style = style)
    }
}
