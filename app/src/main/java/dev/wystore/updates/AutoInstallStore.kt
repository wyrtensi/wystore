package dev.wystore.updates

import android.content.Context

/**
 * Packages whose download finished while "install as soon as it is downloaded" was on.
 *
 * The install itself needs an Activity for Android's confirmation dialog, and a download usually
 * finishes with the app in the background, so the request is written down and acted on the next
 * time the app is on screen. Kept in SharedPreferences rather than memory because the process that
 * downloads is often gone by then.
 */
class AutoInstallStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("wystore_queue", Context.MODE_PRIVATE)

    fun request(packageName: String) {
        if (packageName.isBlank()) return
        // Committed rather than applied: a worker's process can end the moment it returns.
        prefs.edit().putStringSet(KEY, requested() + packageName).commit()
    }

    fun requested(): Set<String> = prefs.getStringSet(KEY, emptySet())?.toSet().orEmpty()

    fun clear(packageName: String) {
        prefs.edit().putStringSet(KEY, requested() - packageName).commit()
    }

    private companion object {
        const val KEY = "auto_install_requested"
    }
}
