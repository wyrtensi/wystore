package dev.wystore.data.library

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.wystore.data.SourceError
import dev.wystore.data.SourceFormatException

/** The public feed describes exact APK sets; malformed references must never become guessed downloads. */
object LibraryJson {
    const val ROOT = "https://raw.githubusercontent.com/wyrtensi/wystore-library/main/library/"
    private const val RELEASES = "https://github.com/wyrtensi/wystore-library/releases/"
    private val packagePattern = Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+")
    private val digestPattern = Regex("[0-9a-f]{64}")

    fun requirePackage(value: String): String = value.also { check(packagePattern.matches(it), "Invalid package") }

    fun relativeUrl(path: String, prefix: String = ""): String {
        check(path.isNotBlank() && !path.startsWith('/') && '\\' !in path && '%' !in path &&
            ':' !in path && '?' !in path && '#' !in path &&
            path.split('/').all { it.isNotEmpty() && it != "." && it != ".." }, "Invalid library path")
        return ROOT + prefix + path
    }

    fun parseCatalog(json: String): LibraryCatalog = parse(json) { root ->
        schema(root, "schema")
        val cards = root.array("apps").map { element ->
            val item = element.obj()
            val pkg = requirePackage(item.text("package"))
            check(item.text("metadataPath") == "apps/$pkg/metadata.json", "Wrong metadata path")
            val icon = item.optionalText("icon")?.let {
                check(it.startsWith("apps/$pkg/images/"), "Wrong icon package")
                relativeUrl(it)
            }
            val locales = item.optionalObject("locales")?.entrySet()?.associate { (locale, value) ->
                val text = value.obj()
                locale to LibrarySearchText(text.text("title"), text.optionalText("shortDescription").orEmpty())
            }.orEmpty()
            val included = item.get("foreign_category")?.let {
                check(it.isJsonPrimitive && it.asJsonPrimitive.isBoolean, "Invalid category membership")
                it.asBoolean
            } ?: true
            val categories = if (item.has("categories")) item.strings("categories").also { slugs ->
                check(slugs.size <= 8 && slugs.distinct().size == slugs.size &&
                    slugs.all { Regex("[a-z][a-z0-9-]{0,63}").matches(it) }, "Invalid app categories")
            } else emptyList()
            LibraryCard(pkg, item.text("title"), item.optionalText("developer").orEmpty(), icon, included, locales, categories)
        }
        check(cards.map { it.packageName }.distinct().size == cards.size, "Duplicate catalogue package")
        LibraryCatalog(cards)
    }

    fun parseIndex(json: String): LibraryIndex = parse(json) { root ->
        schema(root, "schema"); schema(root, "variant_schema")
        val apps = root.array("applications").map { element ->
            val item = element.obj()
            val pkg = requirePackage(item.text("package"))
            val tag = "$pkg-library"
            val artifacts = item.array("artifacts").map { descriptor(it.obj(), pkg, "apk") }
            check(artifacts.map { it.sha256 }.distinct().size == artifacts.size, "Duplicate artifact")
            val byHash = artifacts.associateBy { it.sha256 }
            val variants = item.array("variants").map { value ->
                val variant = value.obj()
                digest(variant.text("profile_fingerprint"))
                val certificates = variant.strings("certificate_sha256").map(::digest).toSet()
                check(certificates.isNotEmpty(), "No signing identity")
                val scope = variant.optionalObject("certificate_scope")?.let {
                    check(it.text("mode") == "android-sdk", "Unknown certificate scope")
                    it.positiveInt("sdk")
                }
                val c = variant.objectValue("compatibility")
                val compatibility = LibraryCompatibility(
                    c.positiveInt("min_sdk"), c.strings("abis"), c.strings("native_abis").toSet(),
                    c.positiveInt("density"), c.text("locale"), c.strings("languages").toSet(),
                    c.strings("required_features").toSet(),
                    if (c.get("gl_es_version")?.isJsonNull == true) 0 else c.boundedInt("gl_es_version", 0),
                    c.strings("required_split_types").toSet(), c.strings("provided_split_types").toSet(),
                    c.strings("uses_splits").toSet()
                )
                val members = variant.array("files").map { file ->
                    val member = file.obj()
                    val hash = digest(member.text("artifact"))
                    check(hash in byHash, "Missing artifact")
                    val name = member.text("original_name")
                    check(Regex("[A-Za-z0-9_.-]+\\.apk").matches(name), "Invalid original filename")
                    val role = member.text("role")
                    val split = member.text("split", allowBlank = true)
                    check((role == "base" && split.isEmpty()) ||
                        (role == "split" && Regex("[A-Za-z0-9_.-]+").matches(split)), "Invalid split identity")
                    LibraryMember(hash, name, role, split)
                }
                check(members.count { it.role == "base" } == 1 &&
                    members.map { it.split }.distinct().size == members.size &&
                    members.map { it.artifact }.distinct().size == members.size, "Invalid complete APK set")
                val id = variant.text("id")
                check(Regex("[A-Za-z0-9_]+").matches(id), "Invalid variant id")
                LibraryVariant(id, variant.integer("version_code", 1),
                    variant.text("version_name", allowBlank = true), certificates, scope, compatibility, members,
                    variant.text("profile_fingerprint"))
            }
            check(variants.map { it.id }.distinct().size == variants.size && variants.isNotEmpty(), "Duplicate/empty variants")
            val default = item.text("default_variant")
            check(variants.any { it.id == default }, "Missing default variant")
            val publication = item.objectValue("publication")
            check(publication.text("tag") == tag && publication.text("release_url") == RELEASES + "tag/$tag", "Wrong release")
            LibraryApplication(pkg, item.text("name"), default, variants, byHash,
                LibraryPublication(tag, publication.text("release_url"),
                    descriptor(publication.objectValue("catalog"), pkg, "catalog"),
                    descriptor(publication.objectValue("compat_manifest"), pkg, "manifest")))
        }
        check(apps.map { it.packageName }.distinct().size == apps.size, "Duplicate package")
        LibraryIndex(apps.associateBy { it.packageName })
    }

    fun parseMetadata(json: String, packageName: String): LibraryMetadata = parse(json) { root ->
        requirePackage(packageName); schema(root, "schema")
        check(root.text("package") == packageName, "Wrong metadata package")
        val locales = root.objectValue("locales").entrySet().associate { (locale, element) ->
            val data = element.obj()
            fun image(path: String) = relativeUrl(path, "apps/$packageName/").also {
                check(path.startsWith("images/"), "Invalid artwork path")
            }
            val play = data.optionalText("playUrl")?.also {
                check(it == "https://play.google.com/store/apps/details?id=$packageName", "Wrong upstream URL")
            }
            locale to LibraryLocale(data.text("title"), data.text("developer"),
                data.optionalText("description").orEmpty(), data.optionalText("shortDescription").orEmpty(),
                data.optionalText("versionName"), data.optionalText("updatedDate"), data.optionalText("changelog"),
                data.optionalText("icon")?.let(::image), data.strings("screenshots").map(::image), play)
        }
        check(locales.isNotEmpty(), "No localized metadata")
        LibraryMetadata(packageName, locales)
    }

    private fun descriptor(item: JsonObject, pkg: String, kind: String): LibraryArtifact {
        val hash = digest(item.text("sha256"))
        val name = if (kind == "apk") "$hash.apk" else "$kind-$hash.json"
        check(item.text("name") == name, "Nonimmutable filename")
        val url = RELEASES + "download/$pkg-library/$name"
        check(item.text("url") == url, "Wrong artifact URL")
        return LibraryArtifact(name, hash, item.integer("size", 1), url)
    }

    private fun digest(value: String): String = value.also { check(digestPattern.matches(it), "Invalid SHA-256") }
    private fun schema(root: JsonObject, field: String) = check(root.integer(field, 1) == 1L, "Unsupported schema")
    private fun JsonElement.obj(): JsonObject { check(isJsonObject, "Expected object"); return asJsonObject }
    private fun JsonObject.objectValue(key: String) = get(key)?.obj() ?: invalid("Missing $key")
    private fun JsonObject.optionalObject(key: String) = get(key)?.takeUnless { it.isJsonNull }?.obj()
    private fun JsonObject.array(key: String): List<JsonElement> {
        val value = get(key) ?: invalid("Missing $key")
        check(value.isJsonArray, "Expected array $key"); return value.asJsonArray.toList()
    }
    private fun JsonObject.text(key: String, allowBlank: Boolean = false): String {
        val value = get(key) ?: invalid("Missing $key")
        check(value.isJsonPrimitive && value.asJsonPrimitive.isString, "Expected string $key")
        return value.asString.also { check(allowBlank || it.isNotBlank(), "Empty $key") }
    }
    private fun JsonObject.optionalText(key: String): String? =
        if (!has(key) || get(key).isJsonNull) null else text(key, allowBlank = true)
    private fun JsonObject.strings(key: String): List<String> = array(key).map {
        check(it.isJsonPrimitive && it.asJsonPrimitive.isString, "Expected string list $key")
        it.asString.also { s -> check(s.isNotBlank(), "Empty $key entry") }
    }
    private fun JsonObject.positiveInt(key: String): Int = boundedInt(key, 1)
    private fun JsonObject.boundedInt(key: String, minimum: Long): Int = integer(key, minimum).also {
        check(it <= Int.MAX_VALUE, "Oversized $key")
    }.toInt()
    private fun JsonObject.integer(key: String, minimum: Long): Long {
        val value = get(key) ?: invalid("Missing $key")
        check(value.isJsonPrimitive && value.asJsonPrimitive.isNumber &&
            Regex("[0-9]+").matches(value.toString()), "Expected integer $key")
        val number = value.asString.toLongOrNull() ?: invalid("Oversized $key")
        check(number >= minimum, "Out of range $key"); return number
    }
    private inline fun <T> parse(json: String, block: (JsonObject) -> T): T = try {
        block(JsonParser.parseString(json).obj())
    } catch (error: com.google.gson.JsonParseException) {
        throw SourceFormatException(SourceError.FORMAT_CHANGED, "Malformed library JSON: ${error.message}")
    }
    private fun check(value: Boolean, reason: String) { if (!value) invalid(reason) }
    private fun invalid(reason: String): Nothing = throw SourceFormatException(SourceError.FORMAT_CHANGED, reason)
}
