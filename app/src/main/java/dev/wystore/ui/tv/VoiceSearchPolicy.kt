package dev.wystore.ui.tv

sealed interface VoiceSearchResult {
    data class Query(val text: String) : VoiceSearchResult
    data object Canceled : VoiceSearchResult
    data object NoMatch : VoiceSearchResult
    data object Failed : VoiceSearchResult
}

/** A completion sound is not a recognition result; empty successful replies must be explained. */
object VoiceSearchPolicy {
    fun result(resultCode: Int, hypotheses: List<String>?): VoiceSearchResult = when (resultCode) {
        0 -> VoiceSearchResult.Canceled
        -1 -> hypotheses?.firstOrNull { it.isNotBlank() }?.trim()?.let(VoiceSearchResult::Query)
            ?: VoiceSearchResult.NoMatch
        else -> VoiceSearchResult.Failed
    }
}
