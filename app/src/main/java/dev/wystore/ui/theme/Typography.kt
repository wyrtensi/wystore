package dev.wystore.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Sans = FontFamily.Default

private fun style(
    weight: FontWeight,
    size: Int,
    lineHeight: Int,
    tracking: Double = 0.0
) = TextStyle(
    fontFamily = Sans,
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
val WyStoreTypography = Typography(
    displayLarge = style(FontWeight.Bold, 52, 60, -0.5),
    displayMedium = style(FontWeight.Bold, 42, 50, -0.25),
    displaySmall = style(FontWeight.Bold, 34, 42),

    headlineLarge = style(FontWeight.Bold, 30, 38, -0.25),
    headlineMedium = style(FontWeight.Bold, 25, 32),
    headlineSmall = style(FontWeight.SemiBold, 21, 28),

    titleLarge = style(FontWeight.SemiBold, 20, 26),
    titleMedium = style(FontWeight.SemiBold, 16, 22, 0.1),
    titleSmall = style(FontWeight.SemiBold, 14, 20, 0.1),

    bodyLarge = style(FontWeight.Normal, 16, 24, 0.15),
    bodyMedium = style(FontWeight.Normal, 14, 20, 0.15),
    bodySmall = style(FontWeight.Normal, 13, 18, 0.2),

    labelLarge = style(FontWeight.SemiBold, 14, 20, 0.1),
    labelMedium = style(FontWeight.Medium, 12, 16, 0.4),
    labelSmall = style(FontWeight.Medium, 11, 15, 0.4)
)
