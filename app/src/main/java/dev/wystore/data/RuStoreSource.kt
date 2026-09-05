package dev.wystore.data

import android.content.Context
import android.os.Build
import dev.wystore.BuildConfig
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class RuStoreSource(context: Context) : StoreSource {
    private val density = context.resources.displayMetrics.densityDpi
    private val repository = StoreRepository(context)
    // Process-wide, so browsing the catalogue reuses one connection instead of opening a TLS
    // session per ViewModel and per worker that happens to construct a source.
    private val client = HttpClients.ruStore(context)
    private val apiClient = RuStoreApiClient(client, repository)

    companion object {
        @Volatile
        private var instance: RuStoreSource? = null

        /**
         * The shared source. Constructing one is cheap on its own, but each instance pulls in a
         * [StoreRepository] and an HTTP client, and ViewModels and workers were each making their
         * own; sharing keeps one warm connection and one settings read for the whole process.
         */
        fun getInstance(context: Context): RuStoreSource = instance ?: synchronized(this) {
            instance ?: RuStoreSource(context.applicationContext).also { instance = it }
        }
    }

    override suspend fun search(query: String, page: Int): SearchPage = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        val route = if (page <= 1) "catalog/search?query=$encoded" else "catalog/search/page-$page?query=$encoded"
        val html = getText("https://www.rustore.ru/$route")
        RustoreHtmlParser.parseSearchPage(html, page)
    }

    override suspend fun details(packageName: String, includeReviews: Boolean): StoreApp = withContext(Dispatchers.IO) {
        val validPackageName = PackageNameValidator.requireValid(packageName)
        val root = parseObject(getText("https://backapi.rustore.ru/applicationData/overallInfo/$validPackageName"))
        val body = root.requiredObject("body")
        body.toStoreApp().also {
            if (it.packageName != validPackageName) throw SourceFormatException("Источник отдал карточку другого пакета")
        }.let { app ->
            if (!includeReviews) app
            else {
                // One fetch feeds both reviews and the changelog.
                val page = runCatching { getText("https://www.rustore.ru/catalog/app/$validPackageName") }
                app.copy(
                    reviews = page.mapCatching { RustoreHtmlParser.parseReviewPreviews(it) }
                        .getOrDefault(emptyList()),
                    changelog = page.mapCatching { RustoreHtmlParser.parseChangelog(it) }.getOrNull()
                )
            }
        }
    }

    override suspend fun resolveArtifacts(app: StoreApp): List<DownloadArtifact> = withContext(Dispatchers.IO) {
        PackageNameValidator.requireValid(app.packageName)
        AndroidSdkCompatibility.requireSupported(app.minSdkVersion, Build.VERSION.SDK_INT)
        val payload = JsonObject().apply {
            addProperty("appId", app.appId)
            addProperty("firstInstall", true)
            addProperty("screenDensity", density)
            addProperty("sdkVersion", Build.VERSION.SDK_INT)
            addProperty("withoutSplits", false)
            add("supportedAbis", com.google.gson.JsonArray().apply {
                Build.SUPPORTED_ABIS.forEach { add(it) }
            })
        }
        val root = apiClient.execute { versionCode ->
            Request.Builder()
                .url("https://backapi.rustore.ru/applicationData/v2/download-link")
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "application/json")
                .header("User-Agent", "WyStore/${BuildConfig.VERSION_NAME}")
                .header("ruStoreVerCode", versionCode.toString())
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
        }.use { response ->
            if (!response.isSuccessful) throw SourceFormatException("RuStore не выдал ссылку для скачивания: HTTP ${response.code}")
            parseObject(response.body?.string() ?: throw SourceFormatException("RuStore не вернул ответ"))
        }
        val urls = root.requiredObject("body").getAsJsonArray("downloadUrls")
            ?: throw SourceFormatException("Источник изменил формат: downloadUrls отсутствует")
        urls.map { item ->
            val objectItem = item.asJsonObject
            val url = objectItem.string("url") ?: throw SourceFormatException("Источник отдал файл без ссылки")
            DownloadArtifact(url, objectItem.long("size") ?: 0L, objectItem.string("hash"))
        }.also {
            if (it.isEmpty()) {
                throw SourceFormatException(
                    "RuStore не нашёл APK для ${AndroidSdkCompatibility.label(Build.VERSION.SDK_INT)} " +
                        "и архитектур ${Build.SUPPORTED_ABIS.joinToString()}"
                )
            }
        }
    }

    override suspend fun categories(): List<StoreCategory> = withContext(Dispatchers.IO) {
        RustoreHtmlParser.parseCategories(getText("https://www.rustore.ru/catalog"))
    }

    override suspend fun catalog(slug: String, page: Int): CatalogPage = withContext(Dispatchers.IO) {
        val section = CatalogSlugPolicy.requireValid(slug)
        val base = if (section.isEmpty()) "catalog" else "catalog/$section"
        val route = if (page <= 1) base else "$base/page-$page"
        RustoreHtmlParser.parseCatalogPage(getText("https://www.rustore.ru/$route"), section, page)
    }

    private fun getText(url: String): String {
        val isApi = java.net.URI(url).host == "backapi.rustore.ru"
        val response = if (isApi) {
            apiClient.execute { versionCode ->
                Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .header("User-Agent", "WyStore/${BuildConfig.VERSION_NAME}")
                    .header("ruStoreVerCode", versionCode.toString())
                    .build()
            }
        } else {
            client.newCall(Request.Builder().url(url).header("User-Agent", "WyStore/${BuildConfig.VERSION_NAME}").build()).execute()
        }
        return response.use {
            if (!it.isSuccessful) throw SourceFormatException("RuStore недоступен: HTTP ${it.code}")
            it.body?.string() ?: throw SourceFormatException("RuStore не вернул ответ")
        }
    }

    private fun parseObject(text: String): JsonObject = try {
        JsonParser.parseString(text).asJsonObject
    } catch (error: Exception) {
        throw SourceFormatException("Источник изменил формат ответа")
    }
}

object RustoreHtmlParser {
    private const val CHANGELOG_HEADING = "Что нового"
    private const val CHANGELOG_VERSION_LABEL = "Версия"
    private const val CHANGELOG_DATE_LABEL = "Дата"

    fun parseSearchPage(html: String, page: Int): SearchPage {
        val document = Jsoup.parse(html)
        val cards = document.select("[data-testid=app-card]")
        val title = document.select("h1").text()
        val total = Regex("найдено\\s+(\\d+)").find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (cards.isEmpty()) {
            // "Nothing matched" and "the markup changed" are indistinguishable if both throw, and
            // the user is told the source broke when their query simply had no results.
            if (!renderedSearchResults(document, total)) {
                throw SourceFormatException("Источник изменил формат поиска")
            }
            return SearchPage(emptyList(), page, total ?: 0)
        }
        return SearchPage(cards.mapNotNull(::parseAppCard), page, total)
    }

    /** True when the page really is a rendered search screen that happens to hold no cards. */
    private fun renderedSearchResults(document: org.jsoup.nodes.Document, total: Int?): Boolean =
        total == 0 ||
            document.selectFirst("[data-testid=search_screen_empty_result]") != null ||
            document.selectFirst("[data-testid=appslist]") != null

    fun parseCategories(html: String): List<StoreCategory> {
        val document = Jsoup.parse(html)
        val links = document.select("a[data-testid=category], a[data-testid=all]")
        if (links.isEmpty()) throw SourceFormatException("Источник изменил формат каталога")
        return links.mapNotNull { link ->
            val slug = link.attr("href").substringAfterLast("/catalog/", "").trim('/')
            val title = link.text().trim()
            if (slug.isBlank() || title.isBlank()) return@mapNotNull null
            if (runCatching { CatalogSlugPolicy.requireValid(slug) }.isFailure) return@mapNotNull null
            StoreCategory(
                slug = slug,
                title = title,
                iconUrl = link.select("img").firstOrNull()?.attr("src")?.takeIf { RustoreUrlPolicy.isTrustedMedia(it) }
            )
        }.distinctBy { it.slug }
    }

    fun parseCatalogPage(html: String, slug: String, page: Int): CatalogPage {
        val document = Jsoup.parse(html)
        val cards = document.select("[data-testid=app-card]")
        if (cards.isEmpty() && document.selectFirst("a[data-testid=category]") == null) {
            // The category nav is on every catalog page, so its absence means the markup changed
            // rather than the section genuinely holding nothing.
            throw SourceFormatException("Источник изменил формат каталога")
        }
        val base = if (slug.isEmpty()) "/catalog" else "/catalog/$slug"
        // The pager lists a window of page links; its highest entry is the last page of the section.
        val lastPage = document.select("a[href^=$base/page-]")
            .mapNotNull { it.attr("href").substringAfterLast("/page-", "").toIntOrNull() }
            .maxOrNull()
            ?.coerceAtLeast(page)
        return CatalogPage(apps = cards.mapNotNull(::parseAppCard), page = page, lastPage = lastPage)
    }

    /**
     * The "Что нового" block. Class names on the page are build-generated hashes, so the block is
     * located by its heading and its fields by the visually-hidden labels the source renders for
     * screen readers, which are far more stable.
     */
    fun parseChangelog(html: String): AppChangelog? {
        val document = Jsoup.parse(html)
        val heading = document.select("h2").firstOrNull { it.text().trim().equals(CHANGELOG_HEADING, true) }
            ?: return null
        val section = heading.parent() ?: return null

        fun labelled(prefix: String): String? = section.select("p").firstOrNull { paragraph ->
            paragraph.selectFirst("span")?.text()?.trim()?.startsWith(prefix, ignoreCase = true) == true
        }?.let { paragraph ->
            paragraph.text().removePrefix(paragraph.selectFirst("span")?.text().orEmpty()).trim()
                .takeIf { it.isNotBlank() }
        }

        val notes = section.select("p")
            .lastOrNull { it.selectFirst("span") == null && it.text().isNotBlank() }
            ?.wholeText()?.trim()
            .orEmpty()
        if (notes.isBlank()) return null
        return AppChangelog(
            versionName = labelled(CHANGELOG_VERSION_LABEL),
            publishedAt = labelled(CHANGELOG_DATE_LABEL),
            notes = notes
        )
    }

    fun parseReviewPreviews(html: String): List<StoreReview> {
        val documents = Jsoup.parse(html).select("script[type=application/ld+json]")
        return documents.flatMap { script ->
            runCatching { JsonParser.parseString(script.data()) }.getOrNull()
                ?.let(::findReviewArrays)
                .orEmpty()
        }.mapNotNull { review ->
            val reviewObject = review.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val text = reviewObject.string("reviewBody")?.trim().orEmpty()
            if (text.isBlank()) return@mapNotNull null
            val author = reviewObject.getAsJsonObject("author")?.string("name")?.trim().orEmpty()
            val rating = reviewObject.getAsJsonObject("reviewRating")?.get("ratingValue")
                ?.takeUnless { it.isJsonNull }?.asInt
            StoreReview(
                author = author.ifBlank { "Пользователь RuStore" },
                publishedAt = reviewObject.string("datePublished"),
                rating = rating?.takeIf { it in 1..5 },
                text = text
            )
        }.distinctBy { listOf(it.author, it.publishedAt, it.text) }.take(5)
    }

    /**
     * Search results and catalog sections render the same card markup, so both routes share this.
     * A card only carries listing data; version and size come from the details endpoint.
     */
    private fun parseAppCard(card: org.jsoup.nodes.Element): StoreApp? {
        val packageName = card.attr("href").substringAfterLast("/catalog/app/", "")
        if (runCatching { PackageNameValidator.requireValid(packageName) }.isFailure) return null
        val paragraphs = card.select("p")
        return StoreApp(
            appId = 0,
            packageName = packageName,
            name = paragraphs.getOrNull(0)?.text().orEmpty(),
            publisher = "",
            categories = listOfNotNull(paragraphs.getOrNull(1)?.text()?.takeIf { it.isNotBlank() }),
            shortDescription = "",
            fullDescription = "",
            iconUrl = card.select("img").firstOrNull()?.attr("src")?.takeIf { RustoreUrlPolicy.isTrustedMedia(it) },
            screenshots = emptyList(),
            rating = card.select("[data-testid=rating]").text().replace(',', '.').toDoubleOrNull(),
            ratingCount = null,
            downloadsText = null,
            versionName = "",
            versionCode = 0,
            updatedAt = null,
            sizeBytes = 0,
            minAndroidVersion = null,
            signatureHint = null,
            sourceVersionId = null
        )
    }

    private fun findReviewArrays(element: JsonElement): List<JsonElement> = when {
        element.isJsonArray -> element.asJsonArray.flatMap(::findReviewArrays)
        !element.isJsonObject -> emptyList()
        else -> element.asJsonObject.entrySet().flatMap { (key, value) ->
            if (key == "review" && value.isJsonArray) value.asJsonArray.toList()
            else findReviewArrays(value)
        }
    }
}

private fun JsonObject.requiredObject(name: String): JsonObject = getAsJsonObject(name)
    ?: throw SourceFormatException("Источник изменил формат: $name отсутствует")

private fun JsonObject.string(name: String): String? = get(name)?.takeUnless { it.isJsonNull }?.asString
private fun JsonObject.long(name: String): Long? = get(name)?.takeUnless { it.isJsonNull }?.asLong
private fun JsonObject.int(name: String): Int? = get(name)?.takeUnless { it.isJsonNull }?.let { value ->
    runCatching { value.asInt }.getOrNull()
}

internal fun JsonObject.toStoreApp(): StoreApp {
    val files = getAsJsonArray("fileUrls")?.mapNotNull { element ->
        element.asJsonObject.takeIf { it.string("type") == "SCREENSHOT" }?.string("fileUrl")?.takeIf { RustoreUrlPolicy.isTrustedMedia(it) }
    }.orEmpty()
    val ratingObject = getAsJsonObject("rating")
    val minSdkVersion = int("minSdkVersion")
    return StoreApp(
        appId = long("appId") ?: throw SourceFormatException("Источник отдал приложение без appId"),
        packageName = string("packageName") ?: throw SourceFormatException("Источник отдал приложение без packageName"),
        name = string("appName").orEmpty(),
        publisher = string("companyName").orEmpty(),
        categories = getAsJsonArray("categories")?.mapNotNull { it.asString }.orEmpty(),
        shortDescription = string("shortDescription").orEmpty(),
        fullDescription = string("fullDescription").orEmpty(),
        iconUrl = string("iconUrl")?.takeIf { RustoreUrlPolicy.isTrustedMedia(it) },
        screenshots = files,
        rating = ratingObject?.get("average")?.takeUnless { it.isJsonNull }?.asDouble,
        ratingCount = ratingObject?.get("votes")?.takeUnless { it.isJsonNull }?.asInt,
        downloadsText = string("roundedDownloadsText"),
        versionName = string("versionName").orEmpty(),
        versionCode = long("versionCode") ?: 0L,
        updatedAt = string("appVerUpdatedAt"),
        sizeBytes = long("fileSize") ?: 0L,
        minAndroidVersion = string("minAndroidVersion") ?: minSdkVersion?.let(AndroidSdkCompatibility::label),
        minSdkVersion = minSdkVersion,
        signatureHint = string("signature"),
        sourceVersionId = long("versionId")
    )
}

object RustoreUrlPolicy {
    fun isTrustedMedia(url: String): Boolean = runCatching {
        val uri = java.net.URI(url)
        val host = uri.host.orEmpty()
        uri.scheme == "https" && (host == "rustore.ru" || host.endsWith(".rustore.ru")) && uri.port in setOf(-1, 443) && uri.userInfo == null
    }.getOrDefault(false)
}
