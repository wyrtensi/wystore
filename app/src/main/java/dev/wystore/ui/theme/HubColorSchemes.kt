package dev.wystore.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The Wy Hub look: near-black glass, one orange accent, cards told apart from the page by a
 * hairline rather than by a lighter fill.
 *
 * Values from the Wy Hub concept. Its cards sit only a few points of lightness above the page and
 * rely on the 1dp light outline to read as cards, so these schemes are not passed through
 * [withSeparatedSurfaces]: widened, the containers turned grey and the hairline stopped mattering.
 *
 * The page is graphite rather than the concept's near-black #0E0E0E: on a whole screen of it the
 * black read as a hole, and the cards on it as the only lit thing.
 */
val HubDarkColorScheme = darkColorScheme(
    primary = Color(0xFFF66C3F),
    onPrimary = Color(0xFF1C0A03),
    primaryContainer = Color(0xFF4A2416),
    onPrimaryContainer = Color(0xFFFFC9B5),
    inversePrimary = Color(0xFFB84A26),

    secondary = Color(0xFFFFA37F),
    onSecondary = Color(0xFF2B1206),
    // Quiet buttons ("Move", "Done" in the concept): grey glass, not tinted.
    secondaryContainer = Color(0xFF31323A),
    onSecondaryContainer = Color(0xFFF3F3F1),

    tertiary = Color(0xFFF2C94C),
    onTertiary = Color(0xFF2A2000),
    tertiaryContainer = Color(0xFF3B3214),
    onTertiaryContainer = Color(0xFFF7E3A0),

    error = Color(0xFFFF8A7A),
    onError = Color(0xFF3B0A04),
    errorContainer = Color(0xFF4A1C17),
    onErrorContainer = Color(0xFFFFDAD4),

    background = Color(0xFF15161A),
    onBackground = Color(0xFFF3F3F1),
    surface = Color(0xFF15161A),
    onSurface = Color(0xFFF3F3F1),
    surfaceVariant = Color(0xFF2E2F35),
    onSurfaceVariant = Color(0xFFA9ABB1),

    surfaceDim = Color(0xFF15161A),
    surfaceBright = Color(0xFF3A3B41),
    surfaceContainerLowest = Color(0xFF101114),
    surfaceContainerLow = Color(0xFF202126),
    surfaceContainer = Color(0xFF25262B),
    surfaceContainerHigh = Color(0xFF2B2C32),
    surfaceContainerHighest = Color(0xFF33343A),

    surfaceTint = Color(0xFFF66C3F),
    inverseSurface = Color(0xFFF3F3F1),
    inverseOnSurface = Color(0xFF1B1C1F),

    outline = Color(0xFF4A4C52),
    // The hairline: white at about a tenth, as on every glass surface of the concept.
    outlineVariant = Color(0x1CFFFFFF),
    scrim = Color(0xFF000000)
)

/**
 * The concept has no light version. This one keeps its parts - one orange accent, white cards
 * with a hairline on a warm page - with the accent darkened until text on it and of it passes
 * contrast on a light background.
 */
val HubLightColorScheme = lightColorScheme(
    primary = Color(0xFFC4501F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDCCD),
    onPrimaryContainer = Color(0xFF3D1405),
    inversePrimary = Color(0xFFFFA37F),

    secondary = Color(0xFF8A4A30),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFECE9E6),
    onSecondaryContainer = Color(0xFF1C1B1A),

    tertiary = Color(0xFF7A5900),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE0A3),
    onTertiaryContainer = Color(0xFF261A00),

    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    background = Color(0xFFF7F6F4),
    onBackground = Color(0xFF1B1B1D),
    surface = Color(0xFFF7F6F4),
    onSurface = Color(0xFF1B1B1D),
    surfaceVariant = Color(0xFFE6E4E1),
    onSurfaceVariant = Color(0xFF5F6066),

    surfaceDim = Color(0xFFE2E0DD),
    surfaceBright = Color(0xFFF7F6F4),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF1EFEC),
    surfaceContainerHigh = Color(0xFFEBE9E6),
    surfaceContainerHighest = Color(0xFFE4E2DF),

    surfaceTint = Color(0xFFC4501F),
    inverseSurface = Color(0xFF2E2F33),
    inverseOnSurface = Color(0xFFF3F3F1),

    outline = Color(0xFF8C8D92),
    outlineVariant = Color(0x1F000000),
    scrim = Color(0xFF000000)
)
