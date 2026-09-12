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
    private val appContext = context.applicationContext
    private val density = context.resources.displayMetrics.densityDpi
    private val repository = StoreRepository(context)
    // Process-wide, so browsing the catalogue reuses one connection instead of opening a TLS
    // session per ViewModel and per worker that happens to construct a source.
    private val client = HttpClients.ruStore(context)
    private val apiClient = RuStoreApiClient(client)

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
            if (it.packageName != validPackageName) throw SourceFormatException(SourceError.WRONG_PACKAGE, "Overview returned another package")
        }.let { app ->
            if (!includeReviews) app
            else {
                // One fetch feeds both reviews and the changelog.
                val page = runCatching { getText("https://www.rustore.ru/catalog/app/$validPackageName") }
                app.copy(
                    reviews = page.mapCatching { RustoreHtmlParser.parseReviews(it) }
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
            // Split parts need an installer session. A device whose firmware refuses sessions
            // installs through the system installer, which takes one file, so it gets the whole
            // APK the source also publishes - larger, and the only thing that device can install.
            addProperty("withoutSplits", dev.wystore.updates.SessionInstallSupport(appContext).sessionsRefused())
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
            if (!response.isSuccessful) throw SourceFormatException(SourceError.RUSTORE_NO_DOWNLOAD_LINK, "download-link HTTP ${response.code}")
            parseObject(response.body?.string() ?: throw SourceFormatException(SourceError.RUSTORE_EMPTY_RESPONSE, "Empty response body"))
        }
        val urls = root.requiredObject("body").getAsJsonArray("downloadUrls")
            ?: throw SourceFormatException(SourceError.RUSTORE_NO_DOWNLOAD_LINK, "downloadUrls missing")
        urls.map { item ->
            val objectItem = item.asJsonObject
            val url = objectItem.string("url") ?: throw SourceFormatException(SourceError.RUSTORE_NO_DOWNLOAD_LINK, "Artifact without url")
            DownloadArtifact(url, objectItem.long("size") ?: 0L, objectItem.string("hash"))
        }.also {
            if (it.isEmpty()) {
                throw SourceFormatException(
                    SourceError.RUSTORE_NO_DOWNLOAD_LINK,
                    "No APK for ${AndroidSdkCompatibility.label(Build.VERSION.SDK_INT)} " +
                        "and ABIs ${Build.SUPPORTED_ABIS.joinToString()}"
                )
            }
        }
    }

    override suspend fun categories(): List<StoreCategory> = withContext(Dispatchers.IO) {
        RustoreHtmlParser.parseCategories(getText("https://www.rustore.ru/catalog"))
    }

    /**
     * The app page embeds a fixed five reviews, which is why "show more" had nothing to show. The
     * source publishes the rest on a page of their own, in the same embedded format.
     */
    override suspend fun reviews(packageName: String): List<StoreReview> = withContext(Dispatchers.IO) {
        val validPackageName = PackageNameValidator.requireValid(packageName)
        RustoreHtmlParser.parseReviews(
            getText("https://www.rustore.ru/catalog/app/$validPackageName/reviews")
        )
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
            if (!it.isSuccessful) {
                // 4xx is an answer about this request - most often a package the store does not
                // carry - and waiting changes nothing. Only 5xx and the like are worth retrying.
                val error = if (it.code in 400..499) {
                    SourceError.RUSTORE_NOT_FOUND
                } else {
                    SourceError.RUSTORE_UNAVAILABLE
                }
                throw SourceFormatException(error, "RuStore HTTP ${it.code}")
            }
            it.body?.string() ?: throw SourceFormatException(SourceError.RUSTORE_EMPTY_RESPONSE, "Empty response body")
        }
    }

    private fun parseObject(text: String): JsonObject = try {
        JsonParser.parseString(text).asJsonObject
    } catch (error: Exception) {
        throw SourceFormatException(SourceError.FORMAT_CHANGED, "Unparseable response")
    }
}

object RustoreHtmlParser {
    /** Upper bound on parsed reviews: a guard against a pathological document, not a preview cap. */
    private const val MAX_REVIEWS = 100
    private const val BACKSLASH = '\u005C'
    private const val REVIEWS_KEY = "\"reviews\":["

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
                throw SourceFormatException(SourceError.FORMAT_CHANGED, "Search markup changed")
            }
            return SearchPage(emptyList(), page, total ?: 0)
        }
        return SearchPage(
            cards.mapNotNull(::parseAppCard)
                .filter { it.name.isNotBlank() }
                .distinctBy { it.packageName },
            page,
            total
        )
    }

    /** True when the page really is a rendered search screen that happens to hold no cards. */
    private fun renderedSearchResults(document: org.jsoup.nodes.Document, total: Int?): Boolean =
        total == 0 ||
            document.selectFirst("[data-testid=search_screen_empty_result]") != null ||
            document.selectFirst("[data-testid=appslist]") != null

    fun parseCategories(html: String): List<StoreCategory> {
        val document = Jsoup.parse(html)
        val links = document.select("a[data-testid=category], a[data-testid=all]")
        if (links.isEmpty()) throw SourceFormatException(SourceError.FORMAT_CHANGED, "Catalog markup changed")
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
            throw SourceFormatException(SourceError.FORMAT_CHANGED, "Catalog markup changed")
        }
        val base = if (slug.isEmpty()) "/catalog" else "/catalog/$slug"
        // The pager lists a window of page links; its highest entry is the last page of the section.
        val lastPage = document.select("a[href^=$base/page-]")
            .mapNotNull { it.attr("href").substringAfterLast("/page-", "").toIntOrNull() }
            .maxOrNull()
            ?.coerceAtLeast(page)
        return CatalogPage(
            // The landing page emits an anchor for every app but renders only some of them on the
            // server; the rest arrive empty and are filled in by its own scripts. Those carry no
            // name and no icon, so keeping them put blank rows in the list. The same app also
            // appears on more than one rail, hence the de-duplication.
            apps = cards.mapNotNull(::parseAppCard)
                .filter { it.name.isNotBlank() }
                .distinctBy { it.packageName },
            page = page,
            lastPage = lastPage
        )
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

    /**
     * Every review embedded in the page.
     *
     * This used to end in `take(5)`, which threw away whatever else the page carried without
     * telling anyone; the app page pages through the full list instead. The remaining bound is a
     * guard against an unexpectedly huge document, not an editorial choice.
     */
    /**
     * Every review the page carries, with everything it says about them.
     *
     * The page renders reviews from a payload embedded for its own use, and that payload has what
     * the schema.org block beside it does not: the developer's reply, the votes, whether the review
     * was edited after posting. A reply is often the answer to the complaint above it, so dropping
     * it left the worst reviews looking unanswered when they had been answered.
     *
     * Falls back to the schema.org block, which is a stable published format and needs no knowledge
     * of how the page is built. That is the point of the fallback: this reads a private payload,
     * and the day its shape changes the reviews go back to what they were rather than disappearing.
     */
    fun parseReviews(html: String): List<StoreReview> =
        runCatching { parseEmbeddedReviews(html) }.getOrDefault(emptyList())
            .ifEmpty { parseReviewPreviews(html) }

    private fun parseEmbeddedReviews(html: String): List<StoreReview> {
        // The payload is a JSON string inside a script, so every quote in it is escaped once.
        val text = html.replace("\\\"", "\"")
        val start = text.indexOf(REVIEWS_KEY)
        if (start < 0) return emptyList()
        val array = balancedArray(text, start + REVIEWS_KEY.length - 1) ?: return emptyList()
        val parsed = JsonParser.parseString(array).takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
        return parsed.mapNotNull { element ->
            val review = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val body = review.string("comment")?.let { RuStoreText.decodeEscapes(it) }?.trim().orEmpty()
            if (body.isBlank()) return@mapNotNull null
            StoreReview(
                author = review.string("firstName")
                    ?.let { RuStoreText.decodeEscapes(it) }?.trim().orEmpty(),
                publishedAt = review.string("commentDate"),
                rating = review.get("rating")?.takeUnless { it.isJsonNull }?.asInt?.takeIf { it in 1..5 },
                text = body,
                likes = review.get("likesCount")?.takeUnless { it.isJsonNull }?.asInt ?: 0,
                dislikes = review.get("dislikesCount")?.takeUnless { it.isJsonNull }?.asInt ?: 0,
                developerResponse = review.string("devResponse")
                    ?.let { RuStoreText.decodeEscapes(it) }?.trim()?.takeIf { it.isNotBlank() },
                developerRespondedAt = review.string("devResponseDate"),
                edited = review.string("editedAt") != null
            )
        }.distinctBy { listOf(it.author, it.publishedAt, it.text) }.take(MAX_REVIEWS)
    }

    /** The array starting at [open], with quotes and nesting inside it respected. */
    private fun balancedArray(text: String, open: Int): String? {
        if (text.getOrNull(open) != '[') return null
        var depth = 0
        var inString = false
        var escaped = false
        for (index in open until text.length) {
            val character = text[index]
            when {
                inString && escaped -> escaped = false
                inString && character == BACKSLASH -> escaped = true
                inString && character == '"' -> inString = false
                inString -> Unit
                character == '"' -> inString = true
                character == '[' -> depth++
                character == ']' -> {
                    depth--
                    if (depth == 0) return text.substring(open, index + 1)
                }
            }
        }
        return null
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
                // Left blank rather than filled with a Russian placeholder: the review card
                // renders its own fallback from resources.
                author = author,
                publishedAt = reviewObject.string("datePublished"),
                rating = rating?.takeIf { it in 1..5 },
                text = text
            )
        }.distinctBy { listOf(it.author, it.publishedAt, it.text) }.take(MAX_REVIEWS)
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
    ?: throw SourceFormatException(SourceError.FORMAT_CHANGED, "Missing field: $name")

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
        appId = long("appId") ?: throw SourceFormatException(SourceError.FORMAT_CHANGED, "Missing field: appId"),
        packageName = string("packageName") ?: throw SourceFormatException(SourceError.FORMAT_CHANGED, "Missing field: packageName"),
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
