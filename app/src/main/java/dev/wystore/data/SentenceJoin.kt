package dev.wystore.data

/**
 * Small text joins that must read right in every shipped language.
 *
 * The backup-restore snackbar glued two translated sentences with a bare space, so the summary
 * (which ends without a full stop in every language) ran straight into the battery hint.
 */
object SentenceJoin {
    private const val NBSP = ' '
    private val TERMINATORS = ".!?。！？…"

    /** [first] and [second] as two sentences: a stop is added to [first] only when it has none. */
    fun sentences(first: String, second: String): String {
        if (second.isBlank()) return first
        val trimmed = first.trimEnd()
        val last = trimmed.lastOrNull() ?: return second
        val cjk = trimmed.any { it.code >= 0x2E80 }
        val stop = when {
            last in TERMINATORS -> ""
            cjk -> "。"
            else -> "."
        }
        // Chinese sentences are not separated by a space.
        return trimmed + stop + (if (cjk) "" else " ") + second
    }

    /**
     * Keeps what follows the label's colon on one line. "Looked after: 1 of 4" wrapped to
     * "1 of" / "4" beside the ring, leaving a lone number; now the colon is the only place it may break.
     */
    fun keepValueTogether(text: String): String {
        val colon = text.indexOfFirst { it == ':' || it == '：' }
        if (colon < 0) return text
        val head = colon + 1
        val keep = if (text.getOrNull(head) == ' ') head + 1 else head
        return text.substring(0, keep) + text.substring(keep).replace(' ', NBSP)
    }
}
