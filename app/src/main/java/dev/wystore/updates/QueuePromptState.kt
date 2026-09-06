package dev.wystore.updates

import android.content.Context

/**
 * When the user last answered the "install the next one?" prompt.
 *
 * Kept in its own SharedPreferences file rather than in the settings store: it is derived state,
 * not a preference, and it must never be picked up by the settings DataStore migration.
 */
class QueuePromptState(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("wystore_queue", Context.MODE_PRIVATE)

    fun lastAnsweredAt(): Long = prefs.getLong(KEY_ANSWERED_AT, 0L)

    fun recordAnswered(now: Long = System.currentTimeMillis()) {
        // Committed rather than applied: this runs as the user leaves the dialog, and losing it
        // means the prompt comes back on the next launch after they already said no.
        prefs.edit().putLong(KEY_ANSWERED_AT, now).commit()
    }

    private companion object {
        const val KEY_ANSWERED_AT = "offer_next_answered_at"
    }
}
