package dev.wystore.data

import dev.wystore.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class RuStoreApiClient(
    private val client: OkHttpClient,
    private val repository: StoreRepository
) {
    fun execute(request: (Long) -> Request): Response {
        val outcome = executeVersioned(discovered = null, request = request)
        if (outcome.accepted) persist(outcome.versionCode)
        return outcome.value
    }

    fun probe(discovered: Long): Long {
        val outcome = executeVersioned(discovered) { versionCode ->
            Request.Builder()
                .url("https://backapi.rustore.ru/applicationData/overallInfo/dev.wystore.compatibility.probe")
                .header("Accept", "application/json")
                .header("User-Agent", "WyStore/${BuildConfig.VERSION_NAME}")
                .header("ruStoreVerCode", versionCode.toString())
                .build()
        }
        outcome.value.use { response ->
            if (!outcome.accepted || response.code !in setOf(200, 404)) {
                throw SourceFormatException(SourceError.RUSTORE_API_REJECTED, "RuStore HTTP ${response.code}")
            }
        }
        persist(outcome.versionCode)
        return outcome.versionCode
    }

    private fun executeVersioned(discovered: Long?, request: (Long) -> Request): RuStoreApiAttempt<Response> {
        val preferred = repository.ruStoreCompatibility().apiVersionCode
        return RuStoreApiCompatibilityPolicy.execute(
            candidates = RuStoreApiCompatibilityPolicy.candidates(preferred, discovered),
            request = { versionCode -> client.newCall(request(versionCode)).execute() },
            statusCode = { it.code },
            discard = Response::close
        )
    }

    private fun persist(versionCode: Long) {
        if (!repository.hasStoredRuStoreApiVersionCode(versionCode)) {
            repository.saveRuStoreApiVersionCode(versionCode)
        }
    }
}
