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

    /**
     * Whether Manrope has every letter [language] is written with.
     *
     * It covers English, Russian, Belarusian, Ukrainian and Latvian, checked against every string
     * the app ships. Kazakh lacks ғ, қ, ң, ұ and ә, Uzbek the ʻ of oʻ and gʻ, and Chinese is not
     * in it at all; Android would fill the gaps letter by letter from another typeface, so a word
     * came out in two. Those languages keep the system typeface.
     */
    fun brandFontCovers(language: String): Boolean = language.lowercase() in BRAND_FONT_LANGUAGES

    private val BRAND_FONT_LANGUAGES = setOf("en", "ru", "be", "uk", "lv")

    /** Empty for [AppLanguage.SYSTEM], which means "whatever the device asks for". */
    fun languageTag(language: AppLanguage): String = language.tag
}
