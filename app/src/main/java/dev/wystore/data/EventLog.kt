package dev.wystore.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/**
 * The last few things that went wrong, kept so a bug report can say what happened.
 *
 * Until now a failure went to Logcat, which nobody reporting a problem can reach, and to the queue
 * row, which disappears the moment the item is retried or cleared. A short list on disk survives
 * both, and a restart.
 *
 * Deliberately small and deliberately not a full log: it holds what the app already decided was a
 * failure, with no file paths, no URLs and no identifiers of the person - the same things the
 * report it feeds is careful not to carry.
 */
class EventLog(context: Context) {

    private val preferences =
        context.applicationContext.getSharedPreferences("wy_store_events", Context.MODE_PRIVATE)

    fun record(packageName: String, code: String, detail: String?) {
        val trimmed = detail?.take(MAX_DETAIL)
        val entry = JSONObject()
            .put(KEY_AT, System.currentTimeMillis())
            .put(KEY_PACKAGE, packageName)
            .put(KEY_CODE, code)
            .put(KEY_DETAIL, trimmed ?: JSONObject.NULL)
        val kept = JSONArray()
        // Newest last, oldest dropped: a report is read from the bottom, where the problem is.
        read().takeLast(MAX_EVENTS - 1).forEach { event ->
            kept.put(
                JSONObject()
                    .put(KEY_AT, event.at)
                    .put(KEY_PACKAGE, event.packageName)
                    .put(KEY_CODE, event.code)
                    .put(KEY_DETAIL, event.detail ?: JSONObject.NULL)
            )
        }
        kept.put(entry)
        preferences.edit { putString(KEY_EVENTS, kept.toString()) }
    }

    fun read(): List<DiagnosticsEvent> {
        val raw = preferences.getString(KEY_EVENTS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                DiagnosticsEvent(
                    at = item.optLong(KEY_AT),
                    packageName = item.optString(KEY_PACKAGE),
                    code = item.optString(KEY_CODE),
                    detail = item.optString(KEY_DETAIL).takeIf { it.isNotBlank() && it != "null" }
                )
            }
        }.getOrDefault(emptyList())
    }

    fun clear() {
        preferences.edit { remove(KEY_EVENTS) }
    }

    private companion object {
        const val MAX_EVENTS = 50
        const val MAX_DETAIL = 400
        const val KEY_EVENTS = "events"
        const val KEY_AT = "at"
        const val KEY_PACKAGE = "package"
        const val KEY_CODE = "code"
        const val KEY_DETAIL = "detail"
    }
}
