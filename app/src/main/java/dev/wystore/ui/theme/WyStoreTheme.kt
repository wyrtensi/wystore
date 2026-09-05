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
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val isDark = ThemePolicy.isDark(settings.themeMode, systemDark)
    val useDynamic = ThemePolicy.canUseDynamicColor(settings.dynamicColorEnabled, Build.VERSION.SDK_INT)

    val colorScheme = when {
        useDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isDark -> dynamicDarkColorScheme(context)
        useDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !isDark -> dynamicLightColorScheme(context)
        isDark -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = WyStoreShapes,
        typography = WyStoreTypography,
        content = content
    )
}
