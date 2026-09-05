package dev.wystore.localization

import dev.wystore.ui.components.StatusCode
import dev.wystore.ui.components.StatusMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusTextResolverTest {

    @Test
    fun resolvesCheckingInRuAndEn() {
        val message = StatusMessage(StatusCode.CHECKING)
        assertEquals("Проверка обновлений...", StatusTextResolver.resolve(message, isRussian = true))
        assertEquals("Checking for updates...", StatusTextResolver.resolve(message, isRussian = false))
    }

    @Test
    fun resolvesDownloadingWithArgs() {
        val message = StatusMessage(
            StatusCode.DOWNLOADING,
            mapOf("percent" to "45", "downloaded" to "45 MB", "total" to "100 MB")
        )
        val ru = StatusTextResolver.resolve(message, isRussian = true)
        val en = StatusTextResolver.resolve(message, isRussian = false)
        assertTrue(ru.contains("45%"))
        assertTrue(en.contains("45%"))
    }

    @Test
    fun resolvesTerminalAndPermissionStates() {
        assertEquals("Готово к установке", StatusTextResolver.resolve(StatusMessage(StatusCode.READY_TO_INSTALL), isRussian = true))
        assertEquals("Ready to install", StatusTextResolver.resolve(StatusMessage(StatusCode.READY_TO_INSTALL), isRussian = false))

        assertEquals("Разрешите установку из этого источника", StatusTextResolver.resolve(StatusMessage(StatusCode.AWAITING_UNKNOWN_SOURCES_PERMISSION), isRussian = true))
        assertEquals("Grant permission to install apps", StatusTextResolver.resolve(StatusMessage(StatusCode.AWAITING_UNKNOWN_SOURCES_PERMISSION), isRussian = false))

        assertEquals("Несовпадение цифровой подписи", StatusTextResolver.resolve(StatusMessage(StatusCode.FAILED_SIGNATURE), isRussian = true))
        assertEquals("Signature mismatch", StatusTextResolver.resolve(StatusMessage(StatusCode.FAILED_SIGNATURE), isRussian = false))
    }
}
