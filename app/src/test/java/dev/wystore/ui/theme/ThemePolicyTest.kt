package dev.wystore.ui.theme

import dev.wystore.settings.AppLanguage
import dev.wystore.settings.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePolicyTest {

    @Test
    fun isDarkResolvesCorrectly() {
        assertTrue(ThemePolicy.isDark(ThemeMode.DARK, systemDark = false))
        assertTrue(ThemePolicy.isDark(ThemeMode.DARK, systemDark = true))
        assertFalse(ThemePolicy.isDark(ThemeMode.LIGHT, systemDark = false))
        assertFalse(ThemePolicy.isDark(ThemeMode.LIGHT, systemDark = true))

        assertFalse(ThemePolicy.isDark(ThemeMode.SYSTEM, systemDark = false))
        assertTrue(ThemePolicy.isDark(ThemeMode.SYSTEM, systemDark = true))
    }

    @Test
    fun dynamicColorRespectsPlatformSdkAndUserSetting() {
        // API 31+ allows dynamic color if enabled
        assertTrue(ThemePolicy.canUseDynamicColor(dynamicColorEnabled = true, sdkInt = 31))
        assertTrue(ThemePolicy.canUseDynamicColor(dynamicColorEnabled = true, sdkInt = 34))
        assertTrue(ThemePolicy.canUseDynamicColor(dynamicColorEnabled = true, sdkInt = 36))

        // API 31+ disables dynamic color if user turned it off
        assertFalse(ThemePolicy.canUseDynamicColor(dynamicColorEnabled = false, sdkInt = 31))
        assertFalse(ThemePolicy.canUseDynamicColor(dynamicColorEnabled = false, sdkInt = 34))

        // API < 31 never uses dynamic color
        assertFalse(ThemePolicy.canUseDynamicColor(dynamicColorEnabled = true, sdkInt = 30))
        assertFalse(ThemePolicy.canUseDynamicColor(dynamicColorEnabled = true, sdkInt = 26))
    }

    @Test
    fun localeTagResolvesCorrectly() {
        assertEquals("", ThemePolicy.languageTag(AppLanguage.SYSTEM))
        assertEquals("ru", ThemePolicy.languageTag(AppLanguage.RU))
        assertEquals("en", ThemePolicy.languageTag(AppLanguage.EN))
        assertEquals("uk", ThemePolicy.languageTag(AppLanguage.UK))
        assertEquals("be", ThemePolicy.languageTag(AppLanguage.BE))
        assertEquals("kk", ThemePolicy.languageTag(AppLanguage.KK))
        assertEquals("uz", ThemePolicy.languageTag(AppLanguage.UZ))
        assertEquals("lv", ThemePolicy.languageTag(AppLanguage.LV))
        assertEquals("zh", ThemePolicy.languageTag(AppLanguage.ZH))
    }

    @Test
    fun everyLanguageButTheSystemOneNamesItself() {
        // The picker shows these instead of a translated name, so a blank one is an unreachable
        // language; a repeated one is two chips that look the same.
        val named = AppLanguage.entries.filter { it != AppLanguage.SYSTEM }
        assertTrue(named.all { it.tag.isNotEmpty() && it.endonym.isNotEmpty() })
        assertEquals(named.size, named.map { it.tag }.toSet().size)
        assertEquals(named.size, named.map { it.endonym }.toSet().size)
    }
}
