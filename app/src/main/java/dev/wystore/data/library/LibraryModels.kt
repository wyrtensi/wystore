package dev.wystore.data.library

data class LibraryCatalog(val cards: List<LibraryCard>)
data class LibrarySearchText(val title: String, val shortDescription: String)
data class LibraryCard(
    val packageName: String, val title: String, val developer: String,
    val iconUrl: String?, val foreignCategory: Boolean,
    val locales: Map<String, LibrarySearchText>
)
data class LibraryIndex(val applications: Map<String, LibraryApplication>)
class LibraryApplicationMissing(val packageName: String) : IllegalStateException("Application absent from library: $packageName")
data class LibraryApplication(
    val packageName: String, val name: String, val defaultVariant: String,
    val variants: List<LibraryVariant>, val artifacts: Map<String, LibraryArtifact>,
    val publication: LibraryPublication
)
data class LibraryArtifact(val name: String, val sha256: String, val size: Long, val url: String)
data class LibraryPublication(
    val tag: String, val releaseUrl: String,
    val catalog: LibraryArtifact, val compatManifest: LibraryArtifact
)
data class LibraryMember(val artifact: String, val originalName: String, val role: String, val split: String)
data class LibraryVariant(
    val id: String, val versionCode: Long, val versionName: String,
    val certificates: Set<String>, val certificateScopeSdk: Int?,
    val compatibility: LibraryCompatibility, val files: List<LibraryMember>,
    val profileFingerprint: String
)
data class LibraryCompatibility(
    val minSdk: Int, val abis: List<String>, val nativeAbis: Set<String>,
    val density: Int, val locale: String, val languages: Set<String>,
    val requiredFeatures: Set<String>, val glEsVersion: Int,
    val requiredSplitTypes: Set<String>, val providedSplitTypes: Set<String>,
    val usesSplits: Set<String>
)
data class LibraryMetadata(
    val packageName: String, val locales: Map<String, LibraryLocale>
) {
    fun localized(locale: String): LibraryLocale = locales[locale]
        ?: locales.entries.firstOrNull { it.key.substringBefore('_') == locale.substringBefore('_') }?.value
        ?: locales["en_US"] ?: locales.values.first()
}
data class LibraryLocale(
    val title: String, val developer: String, val description: String,
    val shortDescription: String, val versionName: String?, val updatedDate: String?,
    val changelog: String?, val iconUrl: String?, val screenshots: List<String>,
    val playUrl: String?
)
enum class LibraryRefreshKind { NETWORK, HTTP, FORMAT }
data class LibraryRefreshError(val kind: LibraryRefreshKind, val cause: Exception)
data class LibraryRead<T>(val value: T, val stale: Boolean = false, val error: LibraryRefreshError? = null) {
    fun requireFresh(): T { error?.let { throw it.cause }; return value }
}
