package dev.wystore.ui.theme

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
import dev.wystore.settings.AppSettings

@Composable
fun WyStoreTheme(
    settings: AppSettings,
    /** The TV interface: its fallback palette is the neutral one, see [TvDarkColorScheme]. */
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
        tv && isDark -> TvDarkColorScheme.withSeparatedSurfaces()
        tv -> TvLightColorScheme.withSeparatedSurfaces()
        // The phone's own look is Wy Hub's; see HubDarkColorScheme for why it is not widened.
        isDark -> HubDarkColorScheme
        else -> HubLightColorScheme
    }
    val language = LocalConfiguration.current.locales[0].language
    val typography = remember(language, tv) {
        // The TV keeps the system typeface: read from across a room, its letterforms were chosen
        // for that, and the TV interface was not part of the Wy Hub concept.
        wyStoreTypography(
            family = if (!tv && ThemePolicy.brandFontCovers(language)) Manrope else FontFamily.Default,
            step = if (tv) 0 else 1
        )
    }

    val hub = colorScheme === HubDarkColorScheme || colorScheme === HubLightColorScheme
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
