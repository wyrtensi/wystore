package dev.wystore.data.library

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.wystore.data.HttpClients
import dev.wystore.data.SourceFormatException
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class LibraryHttpException(val status: Int) : IOException("Library HTTP $status")

/** Cache only validated documents; failed refreshes retain data and an explicit failure. */
class LibraryRepository(
    private val client: OkHttpClient,
    private val directory: File,
    private val root: HttpUrl = LibraryJson.ROOT.toHttpUrl(),
    private val freshnessMillis: Long = 6 * 60 * 60 * 1000L,
    private val now: () -> Long = System::currentTimeMillis
) {
    companion object {
        @Volatile private var instance: LibraryRepository? = null
        fun getInstance(context: Context): LibraryRepository = instance ?: synchronized(this) {
            instance ?: LibraryRepository(context.applicationContext).also { instance = it }
        }
    }
    private data class Snapshot(val json: String, val etag: String?, val checkedAt: Long, val generation: Long)
    private val mutex = Mutex()
    private val memory = mutableMapOf<String, Snapshot>()
    private var generation = 0L

    constructor(context: Context) : this(HttpClients.gitHub(context), File(context.filesDir, "library_catalog"))

    suspend fun catalog(forceRefresh: Boolean = false): LibraryRead<LibraryCatalog> =
        read("catalog.json", forceRefresh, LibraryJson::parseCatalog)

    suspend fun index(forceRefresh: Boolean = false): LibraryRead<LibraryIndex> =
        read("index.json", forceRefresh, LibraryJson::parseIndex)

    suspend fun details(packageName: String, locale: String): LibraryRead<LibraryMetadata> {
        LibraryJson.requirePackage(packageName)
        // One metadata record already contains every published locale.
        return read("apps/$packageName/metadata.json", false) { LibraryJson.parseMetadata(it, packageName) }
    }

    private suspend fun <T> read(path: String, force: Boolean, parse: (String) -> T): LibraryRead<T> {
        val requestedGeneration = generation
        return withContext(Dispatchers.IO) { mutex.withLock {
            directory.mkdirs()
            val cache = File(directory, path.replace('/', '_'))
            val existing = memory[path] ?: if (cache.exists()) {
                val record = JsonParser.parseString(cache.readText()).asJsonObject
                Snapshot(record.get("json").asString, record.get("etag")?.takeUnless { it.isJsonNull }?.asString,
                    record.get("checkedAt").asLong, 0).also { parse(it.json); memory[path] = it }
            } else null
            if (existing != null && ((!force && now() - existing.checkedAt < freshnessMillis) ||
                        existing.generation > requestedGeneration)) {
                return@withLock LibraryRead(parse(existing.json))
            }
            try {
                val request = Request.Builder().url(root.resolve(path) ?: throw IOException("Invalid library root"))
                    .header("Cache-Control", "no-cache").apply {
                        existing?.etag?.let { header("If-None-Match", it) }
                    }.build()
                client.newCall(request).execute().use { response ->
                    val text = when {
                        response.code == 304 -> existing?.json ?: throw IOException("304 without cached document")
                        response.isSuccessful -> response.body?.string() ?: throw IOException("Empty library response")
                        else -> throw LibraryHttpException(response.code)
                    }
                    val parsed = parse(text)
                    val next = Snapshot(text, response.header("ETag") ?: existing?.etag, now(), ++generation)
                    val record = JsonObject().apply {
                        addProperty("json", next.json); addProperty("etag", next.etag); addProperty("checkedAt", next.checkedAt)
                    }
                    val temp = File(directory, cache.name + ".tmp")
                    temp.writeText(record.toString())
                    Files.move(temp.toPath(), cache.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                    memory[path] = next
                    LibraryRead(parsed)
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: IOException) {
                fallback(existing, error, if (error is LibraryHttpException) LibraryRefreshKind.HTTP else LibraryRefreshKind.NETWORK, parse)
            } catch (error: SourceFormatException) {
                fallback(existing, error, LibraryRefreshKind.FORMAT, parse)
            }
        } }
    }

    private fun <T> fallback(snapshot: Snapshot?, error: Exception, kind: LibraryRefreshKind, parse: (String) -> T): LibraryRead<T> {
        if (snapshot == null) throw error
        return LibraryRead(parse(snapshot.json), stale = true, error = LibraryRefreshError(kind, error))
    }
}
