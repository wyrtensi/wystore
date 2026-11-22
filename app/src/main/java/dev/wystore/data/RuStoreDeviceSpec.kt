package dev.wystore.data

import android.content.res.Resources
import java.util.Locale

/**
 * Device traits sent to RuStore `v2/download-link` so the backend picks the exact split APKs
 * (ABI, screen density, Android SDK, and system locale) for this device on demand.
 */
object RuStoreDeviceSpec {
    fun systemLocales(): List<String> {
        val locales = runCatching {
            val list = Resources.getSystem().configuration.locales
            List(list.size()) { index -> list[index] }
        }.getOrElse { listOf(Locale.getDefault()) }
        return normalizeLocales(locales.map { it.toLanguageTag() })
    }

    fun normalizeLocales(rawTags: List<String>): List<String> = rawTags
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.equals("und", ignoreCase = true) }
        .distinct()
        .ifEmpty { listOf("ru-RU") }
}
