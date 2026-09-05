package dev.wystore.data

import android.content.Context
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * The app's HTTP clients, shared for the life of the process.
 *
 * Every source used to build its own [OkHttpClient], and the sources themselves are constructed
 * per ViewModel and per worker. That gave each of them a private connection pool and thread pool,
 * so nothing was ever reused: opening the catalogue, then a category, then an app page meant three
 * TLS handshakes to the same host. One client per host family fixes that without changing any
 * request.
 *
 * Timeouts stay per use: a catalogue page must fail fast, an APK download must not.
 */
object HttpClients {

    private const val CACHE_DIRECTORY = "http_cache"
    private const val CACHE_BYTES = 24L * 1024 * 1024

    @Volatile
    private var ruStoreClient: OkHttpClient? = null

    @Volatile
    private var gitHubClient: OkHttpClient? = null

    /**
     * Client for rustore.ru and its API. Carries the bundled Russian roots and does not follow
     * redirects, because the download path checks every hop against its own allowlist.
     */
    fun ruStore(context: Context): OkHttpClient = ruStoreClient ?: synchronized(this) {
        ruStoreClient ?: build(context).also { ruStoreClient = it }
    }

    fun gitHub(context: Context): OkHttpClient = gitHubClient ?: synchronized(this) {
        gitHubClient ?: ruStore(context).newBuilder()
            .followRedirects(true)
            .followSslRedirects(true)
            .readTimeout(45, TimeUnit.SECONDS)
            .build()
            .also { gitHubClient = it }
    }

    /**
     * A variant of the shared client with different timeouts. Derived with [OkHttpClient.newBuilder]
     * so it keeps the same connection pool, cache and dispatcher as everything else.
     */
    fun withTimeouts(
        context: Context,
        connectSeconds: Long,
        readSeconds: Long
    ): OkHttpClient = ruStore(context).newBuilder()
        .connectTimeout(connectSeconds, TimeUnit.SECONDS)
        .readTimeout(readSeconds, TimeUnit.SECONDS)
        .build()

    private fun build(context: Context): OkHttpClient {
        val builder = RussianTrustStore.createClient(context, 20, 60).newBuilder()
            .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
            .dispatcher(
                Dispatcher().apply {
                    maxRequests = 24
                    maxRequestsPerHost = 6
                }
            )
            .retryOnConnectionFailure(true)

        // A response cache only helps where the server sends caching headers; where it does not,
        // OkHttp simply never stores the entry. Failing to create the directory must not take the
        // network layer down with it.
        runCatching {
            val directory = File(context.applicationContext.cacheDir, CACHE_DIRECTORY)
            builder.cache(Cache(directory, CACHE_BYTES))
        }

        return builder.build()
    }
}
