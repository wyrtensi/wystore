package dev.wystore.localization

import dev.wystore.settings.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which language the picker shows as chosen.
 *
 * From Android 13 the system has a per-app language picker of its own, fed by the same
 * `locales_config.xml`. A language chosen there never passes through this app's settings screen, so
 * the stored value stays SYSTEM while the interface is in Ukrainian - and the screen used to show
 * "System" selected above a Ukrainian interface.
 */
class AppLanguageTagsTest {

    @Test
    fun `a language chosen in this app answers for itself`() {
        assertEquals(AppLanguage.UK, AppLanguageTags.resolve(AppLanguage.UK, "uk"))
        // Even if the system somehow reports something else, the stored choice is the one the user
        // made here and the screen must keep showing it.
        assertEquals(AppLanguage.LV, AppLanguageTags.resolve(AppLanguage.LV, "ru"))
    }

    @Test
    fun `a language chosen in Android's own settings is shown as chosen`() {
        assertEquals(AppLanguage.BE, AppLanguageTags.resolve(AppLanguage.SYSTEM, "be"))
        assertEquals(AppLanguage.KK, AppLanguageTags.resolve(AppLanguage.SYSTEM, "kk-KZ"))
        assertEquals(AppLanguage.ZH, AppLanguageTags.resolve(AppLanguage.SYSTEM, "zh-Hans-CN"))
    }

    @Test
    fun `the first tag decides, because that is the one Android applies`() {
        assertEquals(AppLanguage.UZ, AppLanguageTags.resolve(AppLanguage.SYSTEM, "uz-Latn,ru"))
    }

    @Test
    fun `nothing applied means the device decides`() {
        assertEquals(AppLanguage.SYSTEM, AppLanguageTags.resolve(AppLanguage.SYSTEM, ""))
    }

    @Test
    fun `a language the app does not ship falls back to the device`() {
        // The system picker only offers what locales_config lists, but a locale can also arrive
        // from a restore or a shell command; showing no chip is honest, inventing one is not.
        assertEquals(AppLanguage.SYSTEM, AppLanguageTags.resolve(AppLanguage.SYSTEM, "fr-FR"))
    }
}
