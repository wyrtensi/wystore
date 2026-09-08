package dev.wystore.updates

import android.app.ActivityManager.RunningAppProcessInfo

/**
 * Whether Android will let this process raise the install confirmation right now.
 *
 * The result receiver used to call startActivity unconditionally. That was fine while every
 * install started from a tap, but a download that installs on its own can ask at a moment when
 * the app is nowhere on screen - and a background activity start is refused silently, leaving the
 * queue row stuck in INSTALLING with nothing ever coming back for it.
 *
 * A foreground service does not count: since Android 12 it may not start an activity either, and
 * that is exactly what the download worker is running as.
 */
object PendingUserActionPolicy {

    fun canConfirmNow(importance: Int): Boolean =
        importance <= RunningAppProcessInfo.IMPORTANCE_FOREGROUND
}
