package dev.wystore.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageNameValidatorTest {
    @Test
    fun acceptsNormalAndroidApplicationId() {
        assertEquals("com.yandex.bank", PackageNameValidator.requireValid("com.yandex.bank"))
    }

    @Test
    fun rejectsPathLikeApplicationId() {
        assertFalse(runCatching { PackageNameValidator.requireValid("../../cache/evil") }.isSuccess)
    }

    @Test
    fun permitsOnlyTrustedRustoreMedia() {
        assertTrue(RustoreUrlPolicy.isTrustedMedia("https://static.rustore.ru/icon.png"))
        assertTrue(RustoreUrlPolicy.isTrustedMedia("https://static-m.rustore.ru/icon.png"))
        assertFalse(RustoreUrlPolicy.isTrustedMedia("https://static.rustore.ru.evil.example/icon.png"))
        assertFalse(RustoreUrlPolicy.isTrustedMedia("https://tracker.example/icon.png"))
    }
}
