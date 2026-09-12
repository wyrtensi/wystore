package dev.wystore.settings

import dev.wystore.data.StoreSettings

/**
 * Whether saving settings is worth a line of text.
 *
 * Normally it is: a switch flips and nothing else on screen moves, so "Settings saved" is the only
 * sign the change took. A language change is the exception. It is announced by the whole interface
 * changing language - and the toast itself would be written in the language the user has just
 * left, because the text is resolved before the new locale takes effect.
 */
object SettingsSaveAnnouncement {

    fun announces(before: StoreSettings, after: StoreSettings): Boolean {
        if (before.language == after.language) return true
        // Language plus something else still deserves the line: the something else is silent.
        return after.copy(language = before.language) != before
    }
}
