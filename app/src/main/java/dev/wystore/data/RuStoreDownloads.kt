package dev.wystore.data

/**
 * RuStore's download count, which it sends only as Russian text ("12 млн +", "500 тыс. +").
 *
 * Shown as sent, an English app page read "Downloads: 12 млн +". Read back into a number, it is
 * written in the interface language like every other figure on the page.
 */
object RuStoreDownloads {

    private val pattern = Regex("""^\s*(\d+(?:[.,]\d+)?)\s*(тыс|млн|млрд)?\.?\s*\+?\s*$""", RegexOption.IGNORE_CASE)

    private val multipliers = mapOf(
        "тыс" to 1_000L,
        "млн" to 1_000_000L,
        "млрд" to 1_000_000_000L
    )

    /** The lower bound the text states, or null when RuStore sent something else. */
    fun atLeast(text: String): Long? {
        val match = pattern.matchEntire(text) ?: return null
        val number = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val multiplier = multipliers[match.groupValues[2].lowercase()] ?: 1L
        return Math.round(number * multiplier)
    }
}
