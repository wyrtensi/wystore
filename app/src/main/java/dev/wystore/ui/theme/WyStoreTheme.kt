package dev.wystore.ui.theme

import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.SideEffect
import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.graphics.Color
import dev.wystore.settings.AppSettings

@Composable
fun WyStoreTheme(
    settings: AppSettings,
    /** The TV interface: the same palette and typeface, at the type sizes its layouts were measured with. */
    tv: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val isDark = ThemePolicy.isDark(settings.themeMode, systemDark)
    val useDynamic = ThemePolicy.canUseDynamicColor(settings.dynamicColorEnabled, Build.VERSION.SDK_INT)

    // Widened everywhere, dynamic palettes included: every card in the app is a container tone on
    // the surface, and Material's own step between them is a couple of units of lightness - enough
    // on a good screen at full brightness, and not enough anywhere else.
    val colorScheme = when {
        useDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isDark -> dynamicDarkColorScheme(context).withSeparatedSurfaces()
        useDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !isDark -> dynamicLightColorScheme(context).withSeparatedSurfaces()
        // The TV wears the same Wy Hub palette: its graphite ramp is as neutral as the grey one
        // the TV had of its own, and the accent marks the focus as the old cyan did.
        // The phone's own look is Wy Hub's; see HubDarkColorScheme for why it is not widened.
        isDark -> hubDarkColorScheme(Color(settings.accent.argb))
        else -> hubLightColorScheme(Color(settings.accent.argb))
    }
    val language = LocalConfiguration.current.locales[0].language
    val typography = remember(language, tv) {
        // Manrope on the TV too; its sizes stay those its layouts were measured with.
        wyStoreTypography(
            family = if (ThemePolicy.brandFontCovers(language)) Manrope else FontFamily.Default,
            step = if (tv) 0 else 1
        )
    }

    // enableEdgeToEdge() picks the bar icons' colour from the system's dark mode, not from the
    // theme chosen here: a light Wy Store on a phone in dark mode drew a white clock and battery on
    // its light page, where they could not be read. The icons follow the app's own theme.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !isDark
                isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    val hub = !useDynamic
    CompositionLocalProvider(LocalHubStyle provides hub) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = WyStoreShapes,
            typography = typography,
            content = content
        )
    }
}

/**
 * Whether the Wy Hub palette is on, for the few parts drawn differently in it: cards and the
 * navigation bar get its hairline, and the bar marks the open tab in the accent. A Material You
 * palette has no such hairline - its outline tone is opaque and would box every card in grey.
 */
val LocalHubStyle = staticCompositionLocalOf { false }
