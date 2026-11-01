package dev.wystore.data.library

import com.google.gson.JsonParser
import dev.wystore.data.SourceFormatException
import org.junit.Assert.*
import org.junit.Test

class LibraryJsonTest {
    private fun fixture(name: String) = javaClass.getResource("/library/$name.json")!!.readText()

    @Test fun `canonical version and complete files are read independently of legacy entries`() {
        val app = LibraryJson.parseIndex(fixture("index")).applications.getValue("org.example.library")
        assertEquals(100L, app.variants.single().versionCode)
        assertEquals("base.apk", app.variants.single().files.single().originalName)
        assertEquals(100L, app.artifacts.getValue("a".repeat(64)).size)
        assertEquals("c".repeat(64), app.publication.catalog.sha256)
    }

    @Test fun `catalogue defaults inclusion and preserves localized search data`() {
        val card = LibraryJson.parseCatalog(fixture("catalog")).cards.single()
        assertTrue(card.foreignCategory)
        assertEquals("English summary", card.locales.getValue("en_US").shortDescription)
        val json = JsonParser.parseString(fixture("catalog")).asJsonObject
        json.getAsJsonArray("apps")[0].asJsonObject.addProperty("foreign_category", false)
        assertFalse(LibraryJson.parseCatalog(json.toString()).cards.single().foreignCategory)
    }

    @Test fun `metadata language fallback preserves listing version on changelog`() {
        val metadata = LibraryJson.parseMetadata(fixture("metadata"), "org.example.library")
        val locale = metadata.localized("ru_RU")
        assertEquals("Example", locale.title)
        assertEquals("2.0", locale.versionName)
        assertEquals("Listing notes", locale.changelog)
        assertTrue(locale.iconUrl!!.endsWith("/apps/org.example.library/images/icon.png"))
    }

    @Test fun `invalid schema duplicate identities and malformed members are rejected`() {
        val changes: List<(com.google.gson.JsonObject) -> Unit> = listOf(
            { it.addProperty("schema", 2) },
            { it.addProperty("variant_schema", 2) },
            { it.getAsJsonArray("applications").add(it.getAsJsonArray("applications")[0].deepCopy()) },
            { app(it).getAsJsonArray("variants").add(app(it).getAsJsonArray("variants")[0].deepCopy()) },
            { app(it).getAsJsonArray("artifacts").add(app(it).getAsJsonArray("artifacts")[0].deepCopy()) },
            { artifact(it).addProperty("size", 0) },
            { artifact(it).addProperty("size", 1.5) },
            { artifact(it).addProperty("sha256", "invalid") },
            { variant(it).addProperty("version_code", "100") },
            { variant(it).getAsJsonObject("compatibility").addProperty("gl_es_version", 2147483648L) },
            { variant(it).getAsJsonArray("files")[0].asJsonObject.addProperty("artifact", "f".repeat(64)) },
            { variant(it).getAsJsonArray("files")[0].asJsonObject.addProperty("role", "split") },
            { app(it).addProperty("default_variant", "missing") }
        )
        changes.forEachIndexed { index, change ->
            val json = JsonParser.parseString(fixture("index")).asJsonObject
            change(json)
            assertThrows("mutation $index", SourceFormatException::class.java) {
                LibraryJson.parseIndex(json.toString())
            }
        }
    }

    @Test fun `artifact URLs must match the package release and immutable filename`() {
        for (url in listOf("https://evil.test/a.apk",
                "https://github.com/wyrtensi/other/releases/download/org.example.library-library/${"a".repeat(64)}.apk",
                "https://github.com/wyrtensi/wystore-library/releases/download/other.app-library/${"a".repeat(64)}.apk")) {
            val json = JsonParser.parseString(fixture("index")).asJsonObject
            artifact(json).addProperty("url", url)
            assertThrows(SourceFormatException::class.java) { LibraryJson.parseIndex(json.toString()) }
        }
    }

    @Test fun `relative media paths cannot escape the package directory`() {
        for (path in listOf("../other.png", "https://evil.test/icon.png", "images/%2e%2e/icon.png", "images\\icon.png")) {
            val json = JsonParser.parseString(fixture("metadata")).asJsonObject
            json.getAsJsonObject("locales").getAsJsonObject("en_US").addProperty("icon", path)
            assertThrows(SourceFormatException::class.java) {
                LibraryJson.parseMetadata(json.toString(), "org.example.library")
            }
        }
    }

    private fun app(json: com.google.gson.JsonObject) = json.getAsJsonArray("applications")[0].asJsonObject
    private fun variant(json: com.google.gson.JsonObject) = app(json).getAsJsonArray("variants")[0].asJsonObject
    private fun artifact(json: com.google.gson.JsonObject) = app(json).getAsJsonArray("artifacts")[0].asJsonObject
}
