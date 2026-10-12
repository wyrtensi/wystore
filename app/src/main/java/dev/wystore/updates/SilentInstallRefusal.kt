package dev.wystore.updates

import android.content.Context
import android.content.pm.PackageInstaller
import android.os.Build

/**
 * Remembers that this firmware refuses updates without a dialog.
 *
 * Android's contract for USER_ACTION_NOT_REQUIRED is that a declined request comes back as
 * STATUS_PENDING_USER_ACTION and the ordinary dialog runs. HyperOS on Android 16 does not: it aborts
 * the session at once with "Permission denied", no dialog ever shown. The queue read that as the user
 * cancelling, so every update quietly went back to "ready" and "Update all" did nothing at all.
 *
 * Kept per firmware build: an update of the system may change the answer, and is worth one more try.
 */
class SilentInstallRefusal(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun refusedOnThisFirmware(): Boolean = prefs.getString(KEY_FINGERPRINT, null) == Build.FINGERPRINT

    fun record() {
        prefs.edit().putString(KEY_FINGERPRINT, Build.FINGERPRINT).apply()
    }

    companion object {
        private const val PREFS = "silent_install_refusal"
        private const val KEY_FINGERPRINT = "refused_on_fingerprint"

        /**
         * Whether a session's outcome is the system turning the silent request down.
         *
         * Only an abort of a silent session nobody was asked about: once Android has asked for the
         * user's answer ([InstallSessionWriter.SESSION_AWAITING_USER]), an abort is the user's cancel,
         * and bringing the dialog straight back would argue with them.
         */
        fun isRefusal(status: Int, silentRequested: Boolean, sessionState: String?): Boolean =
            status == PackageInstaller.STATUS_FAILURE_ABORTED &&
                silentRequested &&
                sessionState == InstallSessionWriter.SESSION_PREPARED
    }
}
