package dev.wystore.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RuStore returns a full ISO-8601 instant for the publication time. The app page has a "Updated"
 * row, not a timestamp row, and used to print the instant verbatim.
 */
class StoreDateFormatTest {

    @Test
    fun anIsoInstantBecomesADateWithoutTheTimePart() {
        val formatted = formatStoreDate("2026-08-26T14:58:02.517+00:00")

        assertTrue("must not keep the ISO separator: $formatted", formatted?.contains("T") != true)
        assertTrue("must not keep the clock time: $formatted", formatted?.contains(":") != true)
        assertTrue("must mention the year", formatted?.contains("2026") == true)
    }

    @Test
    fun aPlainDateIsAccepted() {
        val formatted = formatStoreDate("2026-08-26")

        assertTrue(formatted?.contains("2026") == true)
        assertTrue(formatted?.contains("T") != true)
    }

    @Test
    fun blankInputYieldsNothingToRender() {
        assertNull(formatStoreDate(null))
        assertNull(formatStoreDate(""))
        assertNull(formatStoreDate("   "))
    }

    @Test
    fun textThatIsNotADateIsPassedThroughRatherThanDropped() {
        // Some listings carry a human phrase where the timestamp would be; losing it would be
        // worse than showing it.
        assertEquals("недавно", formatStoreDate("недавно"))
    }
}
