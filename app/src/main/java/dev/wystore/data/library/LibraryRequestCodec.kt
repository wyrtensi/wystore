package dev.wystore.data.library

import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.wystore.data.SourceError
import dev.wystore.data.SourceFormatException

data class LibraryInstallRequest(val application: LibraryApplication, val variantId: String, val sourceRevision: Long) {
    fun version(): ResolvedLibraryVersion = LibraryVariantPolicy.resolve(application,
        application.variants.single { it.id == variantId })
}

/** Persist a canonical application and exact variant so the same strict parser validates resumed work. */
object LibraryRequestCodec {
    private val gson = GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create()

    fun encode(app: LibraryApplication, variantId: String, sourceRevision: Long): String {
        require(sourceRevision >= 0 && app.variants.any { it.id == variantId })
        val application = gson.toJsonTree(app).asJsonObject.apply {
            add("package", remove("package_name"))
            add("artifacts", JsonArray().apply { app.artifacts.values.forEach { add(gson.toJsonTree(it)) } })
            getAsJsonArray("variants").forEach { value ->
                value.asJsonObject.apply {
                    add("certificate_sha256", remove("certificates"))
                    remove("certificate_scope_sdk")?.let { sdk ->
                        add("certificate_scope", JsonObject().apply { addProperty("mode", "android-sdk"); add("sdk", sdk) })
                    }
                }
            }
        }
        return JsonObject().apply {
            addProperty("schema", 1); addProperty("variant_schema", 1)
            addProperty("selected_variant", variantId); addProperty("source_revision", sourceRevision)
            add("applications", JsonArray().apply { add(application) })
        }.toString()
    }

    fun decode(json: String): LibraryInstallRequest {
        val app = LibraryJson.parseIndex(json).applications.values.singleOrNull()
            ?: invalid("Request must contain one application")
        val root = JsonParser.parseString(json).asJsonObject
        val id = root.get("selected_variant")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
            ?: invalid("Missing selected variant")
        val revision = root.get("source_revision")?.takeIf {
            it.isJsonPrimitive && it.asJsonPrimitive.isNumber && Regex("[0-9]+").matches(it.toString())
        }?.asString?.toLongOrNull() ?: invalid("Invalid source revision")
        if (app.variants.none { it.id == id }) invalid("Selected variant is absent")
        return LibraryInstallRequest(app, id, revision)
    }

    private fun invalid(reason: String): Nothing = throw SourceFormatException(SourceError.FORMAT_CHANGED, reason)
}
