package dev.wystore.localization

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.wystore.settings.AppLanguage
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLocaleTest {

    @Test
    fun appLocaleControllerCanBeInvoked() {
        val localeList = AppLocaleController.toLocaleList(AppLanguage.RU)
        assertNotNull(localeList)
    }
}
