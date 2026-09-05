package dev.wystore.localization

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import dev.wystore.settings.AppLanguage
import dev.wystore.ui.theme.ThemePolicy

object AppLocaleController {

    fun toLocaleList(language: AppLanguage): LocaleListCompat {
        val tag = ThemePolicy.languageTag(language)
        return if (tag.isEmpty()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
    }

    fun apply(language: AppLanguage) {
        val target = toLocaleList(language)
        if (AppCompatDelegate.getApplicationLocales() != target) {
            AppCompatDelegate.setApplicationLocales(target)
        }
    }
}
