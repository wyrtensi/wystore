package dev.wystore.ui.theme

import androidx.compose.ui.unit.*
import dev.wystore.settings.UiScale
import org.junit.Assert.*
import org.junit.Test

class UiDensityTest {
    @Test fun `medium leaves device density and system fonts unchanged`() {
        val source = Density(2.75f, 1.3f)
        val scaled = UiDensity(source, UiScale.MEDIUM)
        assertEquals(2.75f, scaled.density, 0f)
        assertEquals(1.3f, scaled.fontScale, 0f)
        assertEquals(with(source) { 16.sp.toPx() }, with(scaled) { 16.sp.toPx() }, 0.001f)
    }
    @Test fun `app scale changes layout while preserving system font preference`() {
        val source = Density(2f, 1.4f)
        assertEquals(180f, with(UiDensity(source, UiScale.SMALL)) { 100.dp.toPx() }, 0.001f)
        assertEquals(220f, with(UiDensity(source, UiScale.LARGE)) { 100.dp.toPx() }, 0.001f)
        assertEquals(1.4f, UiDensity(source, UiScale.LARGE).fontScale, 0f)
    }
    @Test fun `system nonlinear font conversions survive app scaling`() {
        val source = object : Density {
            override val density = 2f
            override val fontScale = 2f
            override fun TextUnit.toDp(): Dp = (value * 1.5f).dp
            override fun Dp.toSp(): TextUnit = (value / 1.5f).sp
        }
        val scaled = UiDensity(source, UiScale.LARGE)
        assertEquals(52.8f, with(scaled) { 16.sp.toPx() }, 0.001f)
        assertEquals(16f, with(scaled) { 24.dp.toSp().value }, 0.001f)
    }
}
