package dev.wystore.settings

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class AccentPresetTest {

    private fun channel(c: Int): Double {
        val v = c / 255.0
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(argb: Long): Double {
        val r = ((argb shr 16) and 0xFF).toInt()
        val g = ((argb shr 8) and 0xFF).toInt()
        val b = (argb and 0xFF).toInt()
        return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
    }

    private fun contrast(a: Long, b: Long): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    @Test
    fun whiteTextOnEveryAccentIsReadable() {
        AccentPreset.entries.forEach {
            assertTrue("${it.name}: white on it", contrast(it.argb, 0xFFFFFFFF) >= 4.5)
        }
    }

    @Test
    fun everyAccentStandsOffBothPages() {
        AccentPreset.entries.forEach {
            assertTrue("${it.name}: on the dark page", contrast(it.argb, 0xFF15161A) >= 3.0)
            assertTrue("${it.name}: on the light page", contrast(it.argb, 0xFFF7F6F4) >= 3.0)
        }
    }
}
