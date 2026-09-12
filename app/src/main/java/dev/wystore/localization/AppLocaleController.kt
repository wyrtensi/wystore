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

    /** The language the user picked here, in force immediately. */
    fun apply(language: AppLanguage) {
        val target = toLocaleList(language)
        if (AppCompatDelegate.getApplicationLocales() != target) {
            AppCompatDelegate.setApplicationLocales(target)
        }
    }

    /**
     * The same, at startup, where [AppLanguage.SYSTEM] means "leave it alone".
     *
     * From Android 13 the system has a per-app language picker of its own, fed by
     * `locales_config.xml`. Applying an empty locale list on every launch cleared whatever the user
     * had chosen there, so the app answered its own settings screen and ignored Android's.
     */
    fun applyOnStartup(language: AppLanguage) {
        if (language == AppLanguage.SYSTEM) return
        apply(language)
    }

    /**
     * The language the picker should show as chosen - which is not always the one this app stored.
     *
     * A language set from Android's own settings never passes through here, so the stored value is
     * still [AppLanguage.SYSTEM] while the interface is in Ukrainian. The picker reads what is
     * actually in force rather than what was last tapped.
     */
    fun inForce(stored: AppLanguage): AppLanguage =
        AppLanguageTags.resolve(stored, AppCompatDelegate.getApplicationLocales().toLanguageTags())
}

/** The part of [AppLocaleController.inForce] that does not need Android to be running. */
object AppLanguageTags {

    fun resolve(stored: AppLanguage, appliedTags: String): AppLanguage {
        if (stored != AppLanguage.SYSTEM) return stored
        val applied = appliedTags.substringBefore(',').trim()
        if (applied.isEmpty()) return AppLanguage.SYSTEM
        // "uk", but also "uk-UA" and "zh-Hans-CN": the region is the device's business, not ours.
        val primary = applied.substringBefore('-').lowercase()
        return AppLanguage.entries.firstOrNull { it.tag.isNotEmpty() && it.tag == primary }
            ?: AppLanguage.SYSTEM
    }
}
