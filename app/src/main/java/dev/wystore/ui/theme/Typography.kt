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
    family: FontFamily
) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp
)

/**
 * The full Material type scale.
 *
 * Every role is defined: any style left out of a `Typography` falls back to the Material baseline,
 * which mixes different weights and tracking into the same screen — the reason section titles,
 * chips and captions used to look like they came from different apps. Display and headline roles
 * run tighter than the baseline so large text reads as a set with the body copy under it.
 */
fun wyStoreTypography(family: FontFamily = FontFamily.Default) = Typography(
    displayLarge = style(FontWeight.Bold, 52, 60, -0.5, family = family),
    displayMedium = style(FontWeight.Bold, 42, 50, -0.25, family = family),
    displaySmall = style(FontWeight.Bold, 34, 42, family = family),

    headlineLarge = style(FontWeight.Bold, 30, 38, -0.25, family = family),
    headlineMedium = style(FontWeight.Bold, 25, 32, family = family),
    headlineSmall = style(FontWeight.SemiBold, 21, 28, family = family),

    titleLarge = style(FontWeight.SemiBold, 20, 26, family = family),
    titleMedium = style(FontWeight.SemiBold, 16, 22, 0.1, family = family),
    titleSmall = style(FontWeight.SemiBold, 14, 20, 0.1, family = family),

    bodyLarge = style(FontWeight.Normal, 16, 24, 0.15, family = family),
    bodyMedium = style(FontWeight.Normal, 14, 20, 0.15, family = family),
    bodySmall = style(FontWeight.Normal, 13, 18, 0.2, family = family),

    labelLarge = style(FontWeight.SemiBold, 14, 20, 0.1, family = family),
    labelMedium = style(FontWeight.Medium, 12, 16, 0.4, family = family),
    labelSmall = style(FontWeight.Medium, 11, 15, 0.4, family = family)
)
