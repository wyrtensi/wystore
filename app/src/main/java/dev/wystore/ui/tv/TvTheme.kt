package dev.wystore.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme

/** Distance from the screen edges a TV may cut off: 48dp at the sides, 27dp top and bottom. */
val TvOverscanHorizontal = 48.dp
val TvOverscanVertical = 27.dp

/**
 * The TV components' theme, taken from the phone theme already in place.
 *
 * One palette for both: the user's theme choice and dynamic colours reach the TV interface too, and
 * the phone components reused on TV (dialogs, settings) match the TV ones around them.
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
        background = m3.background,
        onBackground = m3.onBackground,
        surface = m3.surface,
        onSurface = m3.onSurface,
        surfaceVariant = m3.surfaceVariant,
        onSurfaceVariant = m3.onSurfaceVariant,
        error = m3.error,
        onError = m3.onError,
        border = m3.primary
    )
    TvMaterialTheme(colorScheme = colors, content = content)
}
