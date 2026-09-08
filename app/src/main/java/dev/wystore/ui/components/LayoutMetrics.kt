package dev.wystore.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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

/**
 * How much room the floating navigation bar takes at the bottom of the screen.
 *
 * The bar used to be laid out as a band the content stopped above: the last card was cut off and
 * below it sat an empty strip of background, which read as a block someone forgot to fill. The
 * content now runs the full height and scrolls behind the bar; this is what each list adds to its
 * own bottom padding so the last item can still be reached.
 *
 * Zero on screens that have no bar of their own - an app page, a GitHub release.
 */
val LocalBottomBarInset = compositionLocalOf { 0.dp }
