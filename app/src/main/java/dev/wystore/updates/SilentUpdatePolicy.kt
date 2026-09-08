package dev.wystore.updates

import android.os.Build

/**
 * Whether an install session may ask Android to skip its confirmation dialog.
 *
 * Since Android 12 an installer can update an app it installed itself without the user tapping
 * anything: it declares UPDATE_PACKAGES_WITHOUT_USER_ACTION and sets USER_ACTION_NOT_REQUIRED on
 * the session. Wy Store never asked, so every update it had already been trusted with still
 * stopped on a dialog.
 *
 * The rule below is deliberately not the whole rule. Android also requires the app being updated
 * to target a recent enough API, and that floor rises with each release; reproducing the table
 * here would mean guessing at the next one. Asking is safe - the request is a request, and when
 * the system does not honour it the session simply reports that user action is needed and the
 * ordinary confirmation flow runs, exactly as before.
 */
object SilentUpdatePolicy {

    fun allows(
        enabled: Boolean,
        sdkInt: Int = Build.VERSION.SDK_INT,
        isUpdate: Boolean,
        installerOfRecord: String?,
        ownPackageName: String
    ): Boolean {
        if (!enabled) return false
        if (sdkInt < Build.VERSION_CODES.S) return false
        // A first install is always the user's decision; the system offers no way around it.
        if (!isUpdate) return false
        // Someone else installed this app, so updating it silently is not ours to do.
        return installerOfRecord == ownPackageName
    }
}
