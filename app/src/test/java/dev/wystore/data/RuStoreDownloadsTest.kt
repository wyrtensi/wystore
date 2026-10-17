package dev.wystore.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuStoreDownloadsTest {
    @Test
    fun readsTheRoundedCounts() {
        assertEquals(12_000_000L, RuStoreDownloads.atLeast("12 млн +"))
        assertEquals(500_000L, RuStoreDownloads.atLeast("500 тыс. +"))
        assertEquals(1_500_000L, RuStoreDownloads.atLeast("1,5 млн +"))
        assertEquals(1_000_000_000L, RuStoreDownloads.atLeast("1 млрд+"))
        assertEquals(100L, RuStoreDownloads.atLeast("100 +"))
    }

    @Test
    fun anythingElseIsLeftAlone() {
        assertNull(RuStoreDownloads.atLeast("много"))
        assertNull(RuStoreDownloads.atLeast(""))
        assertNull(RuStoreDownloads.atLeast("12 млн + скачиваний"))
    }
}
