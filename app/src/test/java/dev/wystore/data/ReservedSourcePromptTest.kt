package dev.wystore.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReservedSourcePromptTest {
    @Test fun reservedErrorsOfferTheirOwnSettingsPage() {
        assertEquals(ReservedSourcePrompt(ReservedSourceTarget.SOURCES, false),
            ReservedSourcePrompt.from(SourceError.GITHUB_RESERVED_LIBRARY, false))
        assertEquals(ReservedSourcePrompt(ReservedSourceTarget.ABOUT, false),
            ReservedSourcePrompt.from(SourceError.GITHUB_RESERVED_SELF, false))
        assertNull(ReservedSourcePrompt.from(SourceError.GITHUB_INVALID_URL, false))
    }

    @Test fun backupFailureKeepsItsContext() {
        assertEquals(ReservedSourcePrompt(ReservedSourceTarget.SOURCES, true),
            ReservedSourcePrompt.from(SourceError.GITHUB_RESERVED_LIBRARY, true))
    }
}
