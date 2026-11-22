package dev.wystore.data

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Sends the `ruStoreVerCode` header the source expects.
 *
 * Uses the latest verified code synced daily from GitHub ([RuStoreVersionSync]) when available,
 * falling back to the baked-in constant in [RuStoreApiCompatibilityPolicy]. If RuStore rejects the
 * header with 417/419, forces one immediate refresh from GitHub before giving up.
 */
class RuStoreApiClient(
    private val client: OkHttpClient,
    private val versionProvider: (forceRefresh: Boolean) -> Long? = { null }
) {

    /**
     * [request] builds the call for one version code; the catalogue header is added here, per
     * [RuStoreCatalogPolicy], so no call site can forget it or send it from a phone.
     */
    fun execute(
        catalogues: List<RuStoreCatalogPolicy.Catalog> = listOf(RuStoreCatalogPolicy.Catalog.PHONE),
        request: (Long) -> Request.Builder
    ): Response = RuStoreCatalogPolicy.execute(
        catalogues = catalogues,
        request = { catalog ->
            val initialCandidates = RuStoreApiCompatibilityPolicy.candidates(versionProvider(false))
            val attempt = RuStoreApiCompatibilityPolicy.execute(
                candidates = initialCandidates,
                request = { versionCode ->
                    val call = with(RuStoreCatalogPolicy) { request(versionCode).forCatalog(catalog) }
                    client.newCall(call.build()).execute()
                },
                statusCode = { it.code },
                discard = Response::close
            )
            if (attempt.accepted) return@execute attempt.value
            val refreshed = versionProvider(true)
            if (refreshed == null || refreshed in initialCandidates) return@execute attempt.value
            attempt.value.close()
            RuStoreApiCompatibilityPolicy.execute(
                candidates = listOf(refreshed),
                request = { versionCode ->
                    val call = with(RuStoreCatalogPolicy) { request(versionCode).forCatalog(catalog) }
                    client.newCall(call.build()).execute()
                },
                statusCode = { it.code },
                discard = Response::close
            ).value
        },
        statusCode = { it.code },
        discard = Response::close
    )
}
