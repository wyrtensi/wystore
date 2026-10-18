package dev.wystore.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import dev.wystore.R
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Manrope, the Wy Hub typeface. Bundled rather than downloaded - downloadable fonts come through
 * Google Play services, which many phones that use RuStore do not have.
 *
 * Four static weights cut from the variable font. The variable file with a weight setting per
 * entry came out in one weight everywhere: the typeface is cached by its resource, so the first
 * setting asked for was the only one ever drawn, and titles looked like body text.
 */
val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold)
)

private fun style(
    weight: FontWeight,
    size: Int,
    lineHeight: Int,
    tracking: Double = 0.0,
    family: FontFamily,
    step: Int
) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = (size + step).sp,
    lineHeight = (lineHeight + step).sp,
    letterSpacing = tracking.sp
)

/**
 * The full Material type scale.
 *
 * Every role is defined: any style left out of a `Typography` falls back to the Material baseline,
 * which mixes different weights and tracking into the same screen — the reason section titles,
 * chips and captions used to look like they came from different apps. Display and headline roles
 * run tighter than the baseline so large text reads as a set with the body copy under it.
 *
 * [step] adds a point to every role on the phone: set in Manrope, whose letters are smaller for
 * their size than the system face's, the scale read a size too small. The TV keeps the scale its
 * layouts were measured with.
 */
fun wyStoreTypography(family: FontFamily = FontFamily.Default, step: Int = 0) = Typography(
    displayLarge = style(FontWeight.Bold, 52, 60, -0.5, family = family, step = step),
    displayMedium = style(FontWeight.Bold, 42, 50, -0.25, family = family, step = step),
    displaySmall = style(FontWeight.Bold, 34, 42, family = family, step = step),

    headlineLarge = style(FontWeight.Bold, 30, 38, -0.25, family = family, step = step),
    headlineMedium = style(FontWeight.Bold, 25, 32, family = family, step = step),
    headlineSmall = style(FontWeight.SemiBold, 21, 28, family = family, step = step),

    titleLarge = style(FontWeight.SemiBold, 20, 26, family = family, step = step),
    titleMedium = style(FontWeight.SemiBold, 16, 22, 0.1, family = family, step = step),
    titleSmall = style(FontWeight.SemiBold, 14, 20, 0.1, family = family, step = step),

    bodyLarge = style(FontWeight.Normal, 16, 24, 0.15, family = family, step = step),
    bodyMedium = style(FontWeight.Normal, 14, 20, 0.15, family = family, step = step),
    bodySmall = style(FontWeight.Normal, 13, 18, 0.2, family = family, step = step),

    labelLarge = style(FontWeight.SemiBold, 14, 20, 0.1, family = family, step = step),
    labelMedium = style(FontWeight.Medium, 12, 16, 0.4, family = family, step = step),
    labelSmall = style(FontWeight.Medium, 11, 15, 0.4, family = family, step = step)
)
