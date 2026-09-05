package dev.wystore.data

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit

class GitHubReleaseSource(context: Context? = null) {
    // Shared when a context is available: release metadata, repository metadata and the asset
    // download all go to github.com and its CDN, and used to open a fresh connection each time.
    private val client = context?.let { HttpClients.gitHub(it) }
        ?: OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .build()

    suspend fun releases(repository: GitHubRepository): List<GitHubRelease> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/${repository.owner}/${repository.name}/releases?per_page=30")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "WyStore/1.0")
            .build()
        val root = client.newCall(request).execute().use { response ->
            when (response.code) {
                404 -> throw SourceFormatException(SourceError.GITHUB_REPOSITORY_NOT_FOUND, "GitHub 404")
                403, 429 -> throw SourceFormatException(SourceError.GITHUB_RATE_LIMITED, "GitHub ${response.code}")
            }
            if (!response.isSuccessful) throw SourceFormatException(SourceError.GITHUB_UNAVAILABLE, "GitHub HTTP ${response.code}")
            JsonParser.parseString(response.body?.string() ?: "[]").asJsonArray
        }
        root.mapNotNull { element -> element.asJsonObject.toRelease() }
    }

    /**
     * Repository metadata for the store-style page: description, stars, licence and the owner
     * avatar used as the app icon. Curated entries ship with a title and summary, but the live
     * description and release data come from here so a shipped entry cannot go stale.
     */
    suspend fun repositoryInfo(repository: GitHubRepository): GitHubRepositoryInfo = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/${repository.owner}/${repository.name}")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "WyStore/1.0")
            .build()
        val root = client.newCall(request).execute().use { response ->
            when (response.code) {
                404 -> throw SourceFormatException(SourceError.GITHUB_REPOSITORY_NOT_FOUND, "GitHub 404")
                403, 429 -> throw SourceFormatException(SourceError.GITHUB_RATE_LIMITED, "GitHub ${response.code}")
            }
            if (!response.isSuccessful) throw SourceFormatException(SourceError.GITHUB_UNAVAILABLE, "GitHub HTTP ${response.code}")
            JsonParser.parseString(response.body?.string() ?: "{}").asJsonObject
        }
        GitHubRepositoryInfo(
            repository = repository,
            description = root.get("description")?.takeUnless { it.isJsonNull }?.asString,
            stars = root.get("stargazers_count")?.takeUnless { it.isJsonNull }?.asInt,
            homepage = root.get("homepage")?.takeUnless { it.isJsonNull }?.asString?.takeIf { it.isNotBlank() },
            avatarUrl = root.getAsJsonObject("owner")
                ?.get("avatar_url")?.takeUnless { it.isJsonNull }?.asString
                ?.takeIf { GitHubUrlPolicy.isTrustedImage(it) },
            topics = root.getAsJsonArray("topics")?.mapNotNull { it.asString }.orEmpty(),
            license = root.getAsJsonObject("license")?.get("spdx_id")?.takeUnless { it.isJsonNull }?.asString,
            archived = root.get("archived")?.takeUnless { it.isJsonNull }?.asBoolean ?: false
        )
    }

    /**
     * Screenshots a project ships in its repository, using the F-Droid/fastlane layout that most
     * Android projects already publish for store listings. Repositories without them simply have
     * no screenshots; that is not an error.
     */
    suspend fun screenshots(repository: GitHubRepository): List<String> = withContext(Dispatchers.IO) {
        for (path in SCREENSHOT_PATHS) {
            val found = runCatching { listImages(repository, path) }.getOrDefault(emptyList())
            if (found.isNotEmpty()) return@withContext found.take(MAX_SCREENSHOTS)
        }
        emptyList()
    }

    private fun listImages(repository: GitHubRepository, path: String): List<String> {
        val request = Request.Builder()
            .url("https://api.github.com/repos/${repository.owner}/${repository.name}/contents/$path")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "WyStore/1.0")
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string().orEmpty()
            val root = runCatching { JsonParser.parseString(body) }.getOrNull() ?: return emptyList()
            if (!root.isJsonArray) return emptyList()
            root.asJsonArray.mapNotNull { element ->
                val entry = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                val name = entry.stringOrNull("name").orEmpty()
                if (!name.matches(IMAGE_NAME)) return@mapNotNull null
                entry.stringOrNull("download_url")?.takeIf { GitHubUrlPolicy.isTrustedImage(it) }
            }.sorted()
        }
    }

    fun parseRepository(input: String): GitHubRepository {
        val normalized = input.trim().removeSuffix("/").removeSuffix(".git")
        val parts = if (normalized.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))) {
            normalized.split('/')
        } else {
            val uri = runCatching { URI(normalized) }.getOrElse { throw SourceFormatException(SourceError.GITHUB_INVALID_URL, "Unparseable repository URL") }
            if (uri.scheme != "https" || uri.host != "github.com") throw SourceFormatException(SourceError.GITHUB_INVALID_URL, "Not a github.com https URL")
            uri.path.trim('/').split('/').filter { it.isNotBlank() }
        }
        if (parts.size != 2 || parts.any { !it.matches(Regex("[A-Za-z0-9_.-]+")) }) {
            throw SourceFormatException(SourceError.GITHUB_INVALID_URL, "Repository URL is not owner/name")
        }
        return GitHubRepository(parts[0], parts[1])
    }
}

/**
 * GitHub sends JSON `null` for optional fields such as a release `name`, `body` or an asset
 * `digest`. Gson returns a JsonNull instance for those, not a Kotlin null, so a plain `?.asString`
 * throws and the whole release list fails to parse. These accessors treat JsonNull as absent.
 */
private val SCREENSHOT_PATHS = listOf(
    "fastlane/metadata/android/en-US/images/phoneScreenshots",
    "metadata/en-US/images/phoneScreenshots",
    "fastlane/metadata/android/en/images/phoneScreenshots"
)

private val IMAGE_NAME = Regex("(?i).+\\.(png|jpe?g|webp)$")

private const val MAX_SCREENSHOTS = 8

private fun JsonObject.stringOrNull(name: String): String? =
    get(name)?.takeUnless { it.isJsonNull }?.asString

private fun JsonObject.longOrNull(name: String): Long? =
    get(name)?.takeUnless { it.isJsonNull }?.asLong

private fun JsonObject.booleanOrNull(name: String): Boolean? =
    get(name)?.takeUnless { it.isJsonNull }?.asBoolean

private fun JsonObject.toRelease(): GitHubRelease? {
    if (booleanOrNull("draft") == true) return null
    val id = longOrNull("id") ?: return null
    val tag = stringOrNull("tag_name") ?: return null
    val assets = getAsJsonArray("assets")?.mapNotNull { assetElement ->
        val asset = assetElement.asJsonObject
        val name = asset.stringOrNull("name") ?: return@mapNotNull null
        if (!name.endsWith(".apk", ignoreCase = true)) return@mapNotNull null
        val url = asset.stringOrNull("browser_download_url") ?: return@mapNotNull null
        GitHubAsset(
            id = asset.longOrNull("id") ?: return@mapNotNull null,
            name = name,
            sizeBytes = asset.longOrNull("size") ?: 0L,
            downloadUrl = url,
            digest = asset.stringOrNull("digest")
        )
    }.orEmpty()
    return GitHubRelease(
        id = id,
        tagName = tag,
        title = stringOrNull("name")?.takeIf { it.isNotBlank() } ?: tag,
        description = stringOrNull("body").orEmpty(),
        publishedAt = stringOrNull("published_at"),
        prerelease = booleanOrNull("prerelease") ?: false,
        assets = assets
    )
}
