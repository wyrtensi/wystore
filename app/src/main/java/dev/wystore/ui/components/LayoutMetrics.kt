package dev.wystore.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/**
 * Fixed widths that hold text.
 *
 * They were all measured once, against the default system font. Turn the font up - the first thing
 * done on a small phone - and the text no longer fits the box it was given: a one-word category
 * name broke mid-word into "Маркетплей / сы", and the labels in the facts table wrapped. A width
 * that carries text grows with the text.
 */
object LayoutMetrics {
    /** Past this they stop growing; a rail of half-screen tiles is no longer a rail. */
    private const val MAX_SCALE = 1.4f

    fun width(base: Dp, fontScale: Float): Dp = base * fontScale.coerceIn(1f, MAX_SCALE)
}

@Composable
@ReadOnlyComposable
fun fontScaledWidth(base: Dp): Dp = LayoutMetrics.width(base, LocalDensity.current.fontScale)
