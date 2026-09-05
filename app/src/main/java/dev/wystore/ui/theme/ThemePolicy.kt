package dev.wystore.ui.theme

import dev.wystore.settings.AppLanguage
import dev.wystore.settings.ThemeMode

object ThemePolicy {

    fun isDark(themeMode: ThemeMode, systemDark: Boolean): Boolean = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    fun canUseDynamicColor(dynamicColorEnabled: Boolean, sdkInt: Int): Boolean =
        dynamicColorEnabled && sdkInt >= 31

    fun languageTag(language: AppLanguage): String = when (language) {
        AppLanguage.SYSTEM -> ""
        AppLanguage.RU -> "ru"
        AppLanguage.EN -> "en"
    }
}
