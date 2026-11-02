package dev.wystore.data.library

import dev.wystore.data.ManagedSource
import dev.wystore.data.StoreApp

object LibraryCatalogPolicy {
    fun foreign(catalog: LibraryCatalog): List<LibraryCard> = catalog.cards.filter { it.foreignCategory }
    fun search(catalog: LibraryCatalog, query: String): List<LibraryCard> {
        val text = query.trim()
        if (text.isEmpty()) return emptyList()
        return catalog.cards.filter { card ->
            (listOf(card.packageName, card.title, card.developer) +
                card.locales.values.flatMap { listOf(it.title, it.shortDescription) })
                .any { it.contains(text, ignoreCase = true) }
        }
    }
    fun cardToApp(card: LibraryCard, locale: String): StoreApp {
        val text = card.locales[locale] ?: card.locales["en_US"]
        return StoreApp(appId = null, packageName = card.packageName, name = LibraryListingText.plain(text?.title ?: card.title),
            publisher = card.developer, categories = if (card.foreignCategory) listOf("wy-foreign") else emptyList(),
            shortDescription = LibraryListingText.plain(text?.shortDescription.orEmpty()), fullDescription = "", iconUrl = card.iconUrl,
            screenshots = emptyList(), rating = null, ratingCount = null, downloadsText = null,
            versionName = "", versionCode = 0, updatedAt = null, sizeBytes = 0, minAndroidVersion = null,
            signatureHint = null, sourceVersionId = null, source = ManagedSource.WY_STORE)
    }
}
