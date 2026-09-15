package dev.wystore.ui.tv

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Typography as TvTypography
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme

/** Distance from the screen edges a TV may cut off: 48dp at the sides, 27dp top and bottom. */
val TvOverscanHorizontal = 48.dp
val TvOverscanVertical = 27.dp

/**
 * The TV components' theme, taken from the phone theme already in place.
 *
 * One palette, one type scale and one set of corners for both: the TV interface is the same app
 * seen from further away, not a different one. The user's theme choice and dynamic colours reach it
 * too, and the phone components reused on TV - dialogs, settings - match the TV ones around them.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvTheme(dark: Boolean, content: @Composable () -> Unit) {
    val m3 = MaterialTheme.colorScheme
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val colors = base.copy(
        primary = m3.primary,
        onPrimary = m3.onPrimary,
        primaryContainer = m3.primaryContainer,
        onPrimaryContainer = m3.onPrimaryContainer,
        secondary = m3.secondary,
        onSecondary = m3.onSecondary,
        secondaryContainer = m3.secondaryContainer,
        onSecondaryContainer = m3.onSecondaryContainer,
        tertiary = m3.tertiary,
        onTertiary = m3.onTertiary,
        background = m3.surface,
        onBackground = m3.onSurface,
        surface = m3.surfaceContainerLow,
        onSurface = m3.onSurface,
        surfaceVariant = m3.surfaceContainerHigh,
        onSurfaceVariant = m3.onSurfaceVariant,
        inverseSurface = m3.inverseSurface,
        inverseOnSurface = m3.inverseOnSurface,
        error = m3.error,
        onError = m3.onError,
        border = m3.primary,
        borderVariant = m3.outlineVariant
    )
    val type = MaterialTheme.typography
    // The phone's scale, a step larger: read from a sofa, the same roles need more size, but the
    // weights and tracking that make the app look like itself stay what they are.
    val typography = TvTypography(
        displayLarge = type.displayLarge.larger(),
        displayMedium = type.displayMedium.larger(),
        displaySmall = type.displaySmall.larger(),
        headlineLarge = type.headlineLarge.larger(),
        headlineMedium = type.headlineMedium.larger(),
        headlineSmall = type.headlineSmall.larger(),
        titleLarge = type.titleLarge.larger(),
        titleMedium = type.titleMedium.larger(),
        titleSmall = type.titleSmall.larger(),
        bodyLarge = type.bodyLarge.larger(),
        bodyMedium = type.bodyMedium.larger(),
        bodySmall = type.bodySmall.larger(),
        labelLarge = type.labelLarge.larger(),
        labelMedium = type.labelMedium.larger(),
        labelSmall = type.labelSmall.larger()
    )
    TvMaterialTheme(colorScheme = colors, typography = typography, content = content)
}

private fun TextStyle.larger(): TextStyle = copy(
    fontSize = fontSize.scaled(),
    lineHeight = lineHeight.scaled()
)

private fun TextUnit.scaled(): TextUnit = if (isSp) (value * TV_TYPE_SCALE).sp else if (isEm) value.em else this

private const val TV_TYPE_SCALE = 1.15f
