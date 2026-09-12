package dev.wystore.settings

import dev.wystore.data.StoreSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When saving settings says so out loud.
 *
 * The line is resolved from resources at the moment of saving, which is before a new locale takes
 * effect - so a language change used to be confirmed in the language the user had just left. It was
 * a Belarusian interface announcing itself in Chinese.
 */
class SettingsSaveAnnouncementTest {

    private val base = StoreSettings()

    @Test
    fun `an ordinary setting says it was saved, because nothing else on screen moves`() {
        assertTrue(SettingsSaveAnnouncement.announces(base, base.copy(wifiOnly = !base.wifiOnly)))
    }

    @Test
    fun `a language change stays quiet - the whole interface is the confirmation`() {
        assertFalse(SettingsSaveAnnouncement.announces(base, base.copy(language = AppLanguage.BE)))
    }

    @Test
    fun `a language change alongside something else still speaks`() {
        // The other change is silent on its own, so it would go unconfirmed.
        assertTrue(
            SettingsSaveAnnouncement.announces(
                base,
                base.copy(language = AppLanguage.BE, wifiOnly = !base.wifiOnly)
            )
        )
    }

    @Test
    fun `saving without changing anything still confirms the save`() {
        assertTrue(SettingsSaveAnnouncement.announces(base, base))
    }
}
