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

    /** Empty for [AppLanguage.SYSTEM], which means "whatever the device asks for". */
    fun languageTag(language: AppLanguage): String = language.tag
}
