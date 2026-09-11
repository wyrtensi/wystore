package dev.wystore.permissions

import android.content.Context
import androidx.core.content.edit

/**
 * Whether Android's notification dialog has already been put to this user.
 *
 * It has to be remembered by the app, because the system does not offer an answer: before the
 * first request and after a permanent refusal, every signal Android gives looks the same. Without
 * this the button would keep raising a dialog that no longer appears, and look broken.
 */
class NotificationPermissionStore(context: Context) {

    private val preferences = context.applicationContext
        .getSharedPreferences("wy_store_permissions", Context.MODE_PRIVATE)

    fun asked(): Boolean = preferences.getBoolean(KEY_ASKED, false)

    fun markAsked() {
        preferences.edit { putBoolean(KEY_ASKED, true) }
    }

    private companion object {
        const val KEY_ASKED = "notifications_requested"
    }
}
