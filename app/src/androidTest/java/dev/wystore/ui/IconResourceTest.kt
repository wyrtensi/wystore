package dev.wystore.ui

import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.wystore.R
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IconResourceTest {

    @Test
    fun requiredIconsExistAndLoadable() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val statIcon = ContextCompat.getDrawable(context, R.drawable.ic_stat_wystore)
        assertNotNull("ic_stat_wystore should exist", statIcon)

        val monochromeIcon = ContextCompat.getDrawable(context, R.drawable.ic_wy_store_monochrome)
        assertNotNull("ic_wy_store_monochrome should exist", monochromeIcon)
    }
}
