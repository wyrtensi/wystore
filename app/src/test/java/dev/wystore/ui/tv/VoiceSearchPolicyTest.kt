package dev.wystore.ui.tv

import org.junit.Assert.*
import org.junit.Test

class VoiceSearchPolicyTest {
    @Test fun `a blank first hypothesis does not discard the recognized words`() {
        assertEquals(VoiceSearchResult.Query("Firefox"), VoiceSearchPolicy.result(-1, listOf("  ", " Firefox ")))
    }
    @Test fun `an empty successful reply is a visible failure`() {
        assertEquals(VoiceSearchResult.NoMatch, VoiceSearchPolicy.result(-1, emptyList()))
    }
    @Test fun `canceling never replaces the current query`() {
        assertEquals(VoiceSearchResult.Canceled, VoiceSearchPolicy.result(0, listOf("unexpected")))
    }
    @Test fun `a recognizer error is not mistaken for cancellation`() {
        assertEquals(VoiceSearchResult.Failed, VoiceSearchPolicy.result(2, null))
    }
}
