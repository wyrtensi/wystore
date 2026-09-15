package dev.wystore.data

import dev.wystore.device.DeviceProfile
import okhttp3.Request

/**
 * Which RuStore catalogue a backapi request is asked of.
 *
 * The source keeps two: the phone catalogue and the TV one. An app published only for TV is "not
 * found" in the phone catalogue, an app published only for phones is "not found" in the TV one, and
 * an app published for both answers in either. What selects the TV catalogue is a `deviceType: TV`
 * header - undocumented, like `ruStoreVerCode`, and observed rather than published.
 *
 * A phone never sends the header. Not `deviceType: MOBILE` either: the source answers that value
 * with "update RuStore". The phone catalogue is simply what a request without the header gets, and
 * a phone asking anything else is how a phone would lose apps it has.
 *
 * A TV asks the TV catalogue first and, on "not found", the phone catalogue. Boxes run phone apps
 * too, and an app that was installed from the phone catalogue still needs its updates.
 */
object RuStoreCatalogPolicy {

    enum class Catalog { PHONE, TV }

    const val DEVICE_TYPE_HEADER = "deviceType"
    private const val NOT_FOUND = 404

    fun catalogues(profile: DeviceProfile): List<Catalog> = when (profile) {
        DeviceProfile.PHONE -> listOf(Catalog.PHONE)
        DeviceProfile.TV -> listOf(Catalog.TV, Catalog.PHONE)
    }

    fun Request.Builder.forCatalog(catalog: Catalog): Request.Builder = when (catalog) {
        Catalog.PHONE -> removeHeader(DEVICE_TYPE_HEADER)
        Catalog.TV -> header(DEVICE_TYPE_HEADER, "TV")
    }

    /**
     * Asks each catalogue in turn until one has the app.
     *
     * Only "not found" moves on: any other answer - success, a server error, a rejected version
     * header - is about the request rather than about which catalogue carries the app, and asking
     * the other catalogue would only hide it.
     */
    fun <T> execute(
        catalogues: List<Catalog>,
        request: (Catalog) -> T,
        statusCode: (T) -> Int,
        discard: (T) -> Unit
    ): T {
        require(catalogues.isNotEmpty()) { "At least one catalogue is required" }
        catalogues.forEachIndexed { index, catalog ->
            val value = request(catalog)
            if (statusCode(value) != NOT_FOUND || index == catalogues.lastIndex) return value
            discard(value)
        }
        error("RuStore catalogue attempts ended unexpectedly")
    }
}
