package dev.wystore.data.library

import android.content.Context
import dev.wystore.data.*

/** Dedicated catalogue adapter; generic GitHub releases never enter this path. */
class WyStoreLibrarySource(private val context: Context) {
    private val repository = LibraryRepository.getInstance(context)
    private fun locale(): String = if (context.resources.configuration.locales[0].language == "ru") "ru_RU" else "en_US"

    suspend fun search(query: String): List<StoreApp> = LibraryCatalogPolicy.search(repository.catalog().value, query)
        .map { LibraryCatalogPolicy.cardToApp(it, locale()) }

    suspend fun foreign(forceRefresh: Boolean = false): LibraryRead<List<StoreApp>> {
        val read = repository.catalog(forceRefresh)
        return LibraryRead(LibraryCatalogPolicy.foreign(read.value).map { LibraryCatalogPolicy.cardToApp(it, locale()) }, read.stale, read.error)
    }

    suspend fun details(packageName: String): LibraryRead<StoreApp> {
        val catalogRead = repository.catalog()
        val metadataRead = repository.details(packageName, locale())
        val indexRead = repository.index()
        val card = catalogRead.value.cards.firstOrNull { it.packageName == packageName }
            ?: throw LibraryApplicationMissing(packageName)
        val metadata = metadataRead.value.localized(locale())
        val app = indexRead.value.applications[packageName]
        val version = app?.let { LibraryVariantPolicy.select(it, LibraryDeviceSpec.current(context)) as? LibrarySelection.Compatible }?.version
        val published = app?.variants?.maxByOrNull { it.versionCode }
        return LibraryRead(LibraryCatalogPolicy.cardToApp(card, locale()).copy(name = LibraryListingText.plain(metadata.title), publisher = metadata.developer,
            shortDescription = LibraryListingText.plain(metadata.shortDescription), fullDescription = LibraryListingText.plain(metadata.description),
            iconUrl = metadata.iconUrl ?: card.iconUrl, screenshots = metadata.screenshots,
            versionName = version?.versionName ?: published?.versionName.orEmpty(),
            versionCode = version?.versionCode ?: published?.versionCode ?: 0,
            sizeBytes = version?.totalBytes ?: 0, updatedAt = metadata.updatedDate,
            minSdkVersion = app?.variants?.firstOrNull { it.id == version?.variantId }?.compatibility?.minSdk,
            signatureHint = version?.preliminaryCertificates(android.os.Build.VERSION.SDK_INT)?.singleOrNull(),
            installationAvailable = version != null,
            upstreamUrl = metadata.playUrl,
            changelog = metadata.changelog?.let { AppChangelog(metadata.versionName, metadata.updatedDate, LibraryListingText.plain(it)) }),
            stale = catalogRead.stale || metadataRead.stale || indexRead.stale,
            error = catalogRead.error ?: metadataRead.error ?: indexRead.error)
    }
}
