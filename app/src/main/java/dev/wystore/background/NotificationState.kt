package dev.wystore.background

import android.content.Context

/**
 * What the app has already told the user.
 *
 * Kept in its own SharedPreferences file rather than in the settings store: it is derived state
 * that a worker writes from a background thread, and it must never be picked up by the settings
 * DataStore migration.
 */
class NotificationState(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("wystore_notifications", Context.MODE_PRIVATE)

    fun lastDigest(category: String): String? = prefs.getString(key(category), null)

    fun recordDigest(category: String, digest: String) {
        // A worker's process can be killed the moment after it posts, so this is committed rather
        // than applied: losing the record means alerting about the same updates twice.
        prefs.edit().putString(key(category), digest).commit()
    }

    fun clear(category: String) {
        prefs.edit().remove(key(category)).commit()
    }

    private fun key(category: String) = "digest_$category"

    companion object {
        const val CATEGORY_READY = "ready"
        const val CATEGORY_ERRORS = "errors"
    }
}
