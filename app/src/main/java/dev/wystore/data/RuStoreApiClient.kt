package dev.wystore.data

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Sends the `ruStoreVerCode` header the source expects.
 *
 * The number is a constant in [RuStoreApiCompatibilityPolicy], not something the app works out for
 * itself: it used to be read from the official RuStore client, which had to be downloaded whole to
 * be read. Nothing here probes the source or looks for a value it will accept beyond the short
 * fallback list, which only exists so a rejected header does not take the catalogue down entirely.
 */
class RuStoreApiClient(private val client: OkHttpClient) {

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
            RuStoreApiCompatibilityPolicy.execute(
                candidates = RuStoreApiCompatibilityPolicy.candidates(),
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
