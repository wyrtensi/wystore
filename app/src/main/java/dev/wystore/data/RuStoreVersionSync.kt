package dev.wystore.data

import android.content.Context
import dev.wystore.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Keeps the cached `ruStoreVerCode` up to date from the daily GitHub feed.
 *
 * Normal calls never block on GitHub: `raw.githubusercontent.com` can be slow or blocked, so
 * routine daily refreshes run in the background while RuStore calls proceed immediately with the
 * cached or baked-in code. Only a forced refresh (after a 417/419 rejection from RuStore) waits
 * synchronously up to 5 seconds for a fresher value.
 */
class RuStoreVersionSync(
    context: Context,
    private val repository: StoreRepository = StoreRepository(context),
    private val client: OkHttpClient = HttpClients.gitHub(context).newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build(),
    private val nowMs: () -> Long = System::currentTimeMillis
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshInFlight = AtomicBoolean(false)
    private val lock = Any()

    @Volatile
    private var lastAttemptAtMs: Long? = null

    fun currentVersionCode(force: Boolean = false): Long? {
        val stored = repository.ruStoreCompatibility()
        val now = nowMs()
        if (!RuStoreVersionFeed.shouldRefresh(now, stored.verifiedAt, lastAttemptAtMs, force)) {
            return usableStored(stored)
        }
        if (!force) {
            if (refreshInFlight.compareAndSet(false, true)) {
                scope.launch {
                    try {
                        refreshSync(force = false)
                    } finally {
                        refreshInFlight.set(false)
                    }
                }
            }
            return usableStored(stored)
        }
        return refreshSync(force = true)
    }

    private fun refreshSync(force: Boolean): Long? = synchronized(lock) {
        val freshStored = repository.ruStoreCompatibility()
        val lockedNow = nowMs()
        if (!RuStoreVersionFeed.shouldRefresh(lockedNow, freshStored.verifiedAt, lastAttemptAtMs, force)) {
            return@synchronized usableStored(freshStored)
        }
        lastAttemptAtMs = lockedNow
        val fetched = fetchRemote()
        if (fetched != null) {
            repository.saveRuStoreCompatibility(
                RuStoreCompatibility(
                    apiVersionCode = fetched.versionCode,
                    verifiedVersionName = fetched.versionName,
                    verifiedVersionCode = fetched.versionCode,
                    verifiedAt = lockedNow
                )
            )
            return@synchronized fetched.versionCode
        }
        usableStored(freshStored)
    }

    private fun usableStored(stored: RuStoreCompatibility): Long? =
        stored.apiVersionCode.takeIf(RuStoreApiCompatibilityPolicy::isUsableRemoteCode)

    private fun fetchRemote(): RemoteRuStoreVersion? = runCatching {
        val request = Request.Builder()
            .url(RuStoreVersionFeed.FEED_URL)
            .header("Accept", "application/json")
            .header("User-Agent", "WyStore/${BuildConfig.VERSION_NAME}")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            RuStoreVersionFeed.parse(body)
        }
    }.getOrNull()
}
