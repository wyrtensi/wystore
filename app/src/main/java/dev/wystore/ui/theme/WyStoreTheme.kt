package dev.wystore.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
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
        useDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isDark -> dynamicDarkColorScheme(context)
        useDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !isDark -> dynamicLightColorScheme(context)
        tv && isDark -> TvDarkColorScheme
        tv -> TvLightColorScheme
        isDark -> DarkColorScheme
        else -> LightColorScheme
    }.withSeparatedSurfaces()

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = WyStoreShapes,
        typography = WyStoreTypography,
        content = content
    )
}
