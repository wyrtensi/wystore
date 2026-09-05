package dev.wystore.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The Wy Store palette: a teal brand hue over a neutral ramp tinted with the same hue, plus a warm
 * amber accent used for ratings and highlights.
 *
 * The surface container roles are spelled out on purpose. `lightColorScheme`/`darkColorScheme`
 * default every unspecified role to Material's baseline (violet) tokens, so a scheme that names
 * only `surface` leaves every card, chip and bar drawn from a palette that does not match the
 * background behind them.
 */
val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006874),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA6EEF9),
    onPrimaryContainer = Color(0xFF001F24),
    inversePrimary = Color(0xFF4FD8EB),

    secondary = Color(0xFF4A6267),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD3E9ED),
    onSecondaryContainer = Color(0xFF051F23),

    // Warm counterweight to the teal: ratings, "curated" marks and other highlights.
    tertiary = Color(0xFF7A5900),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE0A3),
    onTertiaryContainer = Color(0xFF261A00),

    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    background = Color(0xFFF6FAFA),
    onBackground = Color(0xFF171D1E),
    surface = Color(0xFFF6FAFA),
    onSurface = Color(0xFF171D1E),
    surfaceVariant = Color(0xFFDBE4E6),
    onSurfaceVariant = Color(0xFF3F484A),

    // Each step is a clear tone apart from the background: a two-unit difference reads as one flat
    // sheet on a phone screen, which is what made cards disappear into the page.
    surfaceDim = Color(0xFFD3D9D9),
    surfaceBright = Color(0xFFF6FAFA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEAF0F0),
    surfaceContainer = Color(0xFFE3EAEA),
    surfaceContainerHigh = Color(0xFFDCE3E4),
    surfaceContainerHighest = Color(0xFFD5DDDE),

    surfaceTint = Color(0xFF006874),
    inverseSurface = Color(0xFF2B3132),
    inverseOnSurface = Color(0xFFECF1F1),

    outline = Color(0xFF6F797A),
    outlineVariant = Color(0xFFC5CFD1),
    scrim = Color(0xFF000000)
)

val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF4FD8EB),
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF004F58),
    onPrimaryContainer = Color(0xFFA6EEF9),
    inversePrimary = Color(0xFF006874),

    secondary = Color(0xFFB1CBD0),
    onSecondary = Color(0xFF1C3438),
    secondaryContainer = Color(0xFF334B4F),
    onSecondaryContainer = Color(0xFFD3E9ED),

    tertiary = Color(0xFFEDC148),
    onTertiary = Color(0xFF402D00),
    tertiaryContainer = Color(0xFF5C4200),
    onTertiaryContainer = Color(0xFFFFE0A3),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    background = Color(0xFF0E1415),
    onBackground = Color(0xFFDEE3E4),
    surface = Color(0xFF0E1415),
    onSurface = Color(0xFFDEE3E4),
    surfaceVariant = Color(0xFF3F484A),
    onSurfaceVariant = Color(0xFFBFC8CA),

    surfaceDim = Color(0xFF0E1415),
    surfaceBright = Color(0xFF343A3B),
    surfaceContainerLowest = Color(0xFF080D0E),
    surfaceContainerLow = Color(0xFF1A2122),
    surfaceContainer = Color(0xFF1F2728),
    surfaceContainerHigh = Color(0xFF2A3132),
    surfaceContainerHighest = Color(0xFF343B3C),

    surfaceTint = Color(0xFF4FD8EB),
    inverseSurface = Color(0xFFDEE3E4),
    inverseOnSurface = Color(0xFF2B3132),

    outline = Color(0xFF899294),
    outlineVariant = Color(0xFF3F484A),
    scrim = Color(0xFF000000)
)
