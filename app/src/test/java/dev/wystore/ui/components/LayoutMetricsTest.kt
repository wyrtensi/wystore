package dev.wystore.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutMetricsTest {
    @Test
    fun leavesTheDefaultFontSizeAlone() {
        assertEquals(124.dp, LayoutMetrics.width(124.dp, 1f))
    }

    /** The compact phone this was found on runs at 1.15; the tile has to grow with the text. */
    @Test
    fun growsWithTheSystemFontSize() {
        assertTrue(LayoutMetrics.width(124.dp, 1.15f) > 124.dp)
    }

    @Test
    fun stopsGrowingBeforeATileFillsTheScreen() {
        assertEquals(LayoutMetrics.width(124.dp, 1.4f), LayoutMetrics.width(124.dp, 3f))
    }

    @Test
    fun neverShrinksBelowTheMeasuredWidth() {
        assertEquals(124.dp, LayoutMetrics.width(124.dp, 0.85f))
    }
}
