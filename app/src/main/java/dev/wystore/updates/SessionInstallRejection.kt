package dev.wystore.updates

import android.content.Context

/**
 * Whether this device refuses installer sessions from a store like this one.
 *
 * MIUI with "MIUI optimisation" switched on answers every session a third-party app commits with
 * `INSTALL_FAILED_INTERNAL_ERROR: Permission Denied`, while its own installer, reached through the
 * install intent, takes the very same APK. There is nothing an app can set on the session to get
 * past it, and the switch belongs to the user, so the store changes how it installs instead.
 *
 * Learnt from the refusal itself rather than from a build property or a brand, for the reason
 * [dev.wystore.permissions.VendorBackgroundSettings] gives: the same manufacturer ships firmware
 * that does this and firmware that does not, and a switch the user flips changes the answer on the
 * same phone. The first refused install goes back to "ready" with a standing request, and the app
 * hands it to the system installer the moment it is on screen - at once, when the refusal arrives
 * while it is open - so the user sees a dialog rather than an error.
 *
 * Kept once learnt. Turning optimisation back off makes sessions work again, but the system
 * installer works either way, so the only thing a stale answer costs is the session route's
 * nicer extras - which a firmware like this was not honouring anyway.
 */
object SessionInstallRejection {

    /** True for the specific refusal described above, never for an install that really failed. */
    fun isFirmwareRefusal(statusMessage: String?): Boolean {
        val message = statusMessage?.lowercase() ?: return false
        return "install_failed_internal_error" in message && "permission denied" in message
    }
}

/** Where the answer of [SessionInstallRejection] is remembered for this device. */
class SessionInstallSupport(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("wystore_installer", Context.MODE_PRIVATE)

    fun sessionsRefused(): Boolean = prefs.getBoolean(KEY_REFUSED, false)

    fun markSessionsRefused() {
        // Committed: the receiver that learns this may be the last thing its process does.
        prefs.edit().putBoolean(KEY_REFUSED, true).commit()
    }

    private companion object {
        const val KEY_REFUSED = "sessions_refused_by_firmware"
    }
}
