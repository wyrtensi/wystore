package dev.wystore.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

/**
 * Pushes a scheme's card tones away from the page they sit on.
 *
 * Material's container roles step away from the surface by a couple of units of lightness, which is
 * enough to see on a good screen at full brightness and not enough on a cheap one, in daylight, or
 * for an eye that does not separate near-blacks well. Every card in this app is one of those tones
 * on that surface, so where the step fails the whole layout reads as one flat sheet.
 *
 * The step is widened rather than replaced: each container keeps its order and its hue, and only
 * moves further from the surface, so a Material You palette still looks like itself. Applied to the
 * dynamic schemes as well as the built-in ones - the dynamic ones are exactly where the step cannot
 * be checked in advance.
 */
internal fun ColorScheme.withSeparatedSurfaces(): ColorScheme = copy(
    surfaceContainerLowest = surfaceContainerLowest.separatedFrom(surface, 0.6f),
    surfaceContainerLow = surfaceContainerLow.separatedFrom(surface, 1.7f),
    surfaceContainer = surfaceContainer.separatedFrom(surface, 1.7f),
    surfaceContainerHigh = surfaceContainerHigh.separatedFrom(surface, 1.7f),
    surfaceContainerHighest = surfaceContainerHighest.separatedFrom(surface, 1.7f),
    surfaceVariant = surfaceVariant.separatedFrom(surface, 1.4f)
)

/**
 * The same colour, [factor] times further from [reference] in lightness.
 *
 * Away from it in whichever direction it already lay, so a container that is lighter than the page
 * gets lighter and one that is darker gets darker: the ordering the palette established is what
 * makes elevation read, and reversing any of it would be a different design rather than a clearer
 * one. Clamped, so the darkest tone on a black page cannot be pushed past black and vanish.
 */
private fun Color.separatedFrom(reference: Color, factor: Float): Color {
    val self = FloatArray(3)
    val page = FloatArray(3)
    ColorUtils.colorToHSL(toArgb(), self)
    ColorUtils.colorToHSL(reference.toArgb(), page)
    val distance = self[2] - page[2]
    if (distance == 0f) return this
    self[2] = (page[2] + distance * factor).coerceIn(0.03f, 0.97f)
    return Color(ColorUtils.HSLToColor(self))
}
