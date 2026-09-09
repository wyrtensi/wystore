package dev.wystore.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * The first few app icons of a section, kept between runs.
 *
 * A tile that shows what is inside a section is worth more than one showing the section's own
 * pictogram - twenty pictograms in a grid look like twenty of the same thing. Working those out
 * costs a request per section, so the answer is written down: it is a picture of what the section
 * holds, and that does not change between two openings of the app.
 */
class CategoryPreviewStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("wystore_catalog", Context.MODE_PRIVATE)

    fun read(): Map<String, List<String>> = runCatching {
        val root = JSONObject(prefs.getString(KEY, "{}").orEmpty())
        buildMap {
            for (slug in root.keys()) {
                val icons = root.optJSONArray(slug) ?: continue
                val urls = (0 until icons.length()).mapNotNull { icons.optString(it).takeIf { u -> u.isNotBlank() } }
                if (urls.isNotEmpty()) put(slug, urls)
            }
        }
    }.getOrDefault(emptyMap())

    fun write(previews: Map<String, List<String>>) {
        runCatching {
            val root = JSONObject()
            // Bounded: a section that has fallen out of the catalogue would otherwise be kept for
            // as long as the app is installed.
            previews.entries.take(MAX_SECTIONS).forEach { (slug, urls) ->
                root.put(slug, JSONArray().apply { urls.take(MAX_ICONS).forEach { put(it) } })
            }
            prefs.edit().putString(KEY, root.toString()).apply()
        }
    }

    private companion object {
        const val KEY = "category_previews"
        const val MAX_SECTIONS = 60
        const val MAX_ICONS = 5
    }
}
