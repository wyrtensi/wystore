package dev.wystore.data

/**
 * Undoes the escaping that survives one trip too many through RuStore's page.
 *
 * A product page carries its data as a JSON string inside a script tag, so everything in it is
 * escaped twice: once for the inner JSON, once for the string holding it. The reader strips the
 * outer layer for quotes only - enough to parse - which leaves every other escape doubled. Gson
 * then decodes `\\n` to a backslash followed by an n, and a review written in three paragraphs
 * reached the screen as one line with `\n\n` printed in the middle of it.
 *
 * Applied to the text taken out of that payload rather than to the payload itself: unescaping
 * newlines before parsing would put real line breaks inside JSON string literals, which is not
 * JSON any more.
 */
object RuStoreText {

    fun decodeEscapes(text: String): String {
        if (!text.contains('\\')) return text
        val out = StringBuilder(text.length)
        var index = 0
        while (index < text.length) {
            val character = text[index]
            if (character != '\\' || index == text.lastIndex) {
                out.append(character)
                index++
                continue
            }
            when (val escaped = text[index + 1]) {
                'n' -> { out.append('\n'); index += 2 }
                'r' -> { out.append('\r'); index += 2 }
                't' -> { out.append('\t'); index += 2 }
                '"' -> { out.append('"'); index += 2 }
                '/' -> { out.append('/'); index += 2 }
                // A real backslash the author typed, and the one thing that must consume both
                // characters: leaving it to the next round would turn "\\n" into a line break the
                // author never wrote.
                '\\' -> { out.append('\\'); index += 2 }
                'u' -> {
                    val hex = text.substring(index + 2, minOf(index + 6, text.length))
                    val code = hex.takeIf { it.length == 4 }?.toIntOrNull(16)
                    if (code == null) {
                        out.append(character)
                        index++
                    } else {
                        out.append(code.toChar())
                        index += 6
                    }
                }
                else -> {
                    out.append(character).append(escaped)
                    index += 2
                }
            }
        }
        return out.toString()
    }
}
