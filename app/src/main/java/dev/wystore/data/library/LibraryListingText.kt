package dev.wystore.data.library

import org.jsoup.Jsoup

/** Play descriptions and even some titles contain HTML; render text, never executable markup. */
object LibraryListingText {
    fun plain(value: String): String = Jsoup.parse(value).wholeText().trim()
}
