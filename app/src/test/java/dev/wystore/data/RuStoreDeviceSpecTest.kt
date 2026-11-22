package dev.wystore.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RuStoreDeviceSpecTest {

    @Test
    fun preservesOrderAndDeduplicatesLocaleTags() {
        assertEquals(
            listOf("en-US", "ru-RU", "uk-UA"),
            RuStoreDeviceSpec.normalizeLocales(listOf(" en-US ", "ru-RU", "en-US", "", "und", "uk-UA"))
        )
    }

    @Test
    fun fallsBackToRussianWhenNoValidLocaleIsReported() {
        assertEquals(listOf("ru-RU"), RuStoreDeviceSpec.normalizeLocales(emptyList()))
        assertEquals(listOf("ru-RU"), RuStoreDeviceSpec.normalizeLocales(listOf("", "  ", "und")))
    }
}
