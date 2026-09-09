package dev.wystore.data

import android.content.Context
import android.net.ConnectivityManager

/**
 * Whether a download the user just asked for should be questioned first.
 *
 * "Wi-Fi only" has never applied to a button someone pressed: a transfer the user is waiting for
 * runs on whatever connection there is, on purpose - waiting for Wi-Fi that may never come turns
 * "Install" into a row that sits in the queue saying nothing. The cost of that is the opposite
 * surprise: on mobile data the setting said Wi-Fi only and a hundred megabytes went out anyway,
 * without a word. Asking is what both settings can be true at once.
 */
object MeteredDownloadPolicy {

    /**
     * Only when all three hold: the user asked for Wi-Fi only, the connection charges for this, and
     * they have not already answered. Nothing is asked on Wi-Fi, and nothing is asked of someone
     * who chose "mobile data is fine".
     */
    fun requiresConsent(
        wifiOnly: Boolean,
        allowMobileData: Boolean,
        isMetered: Boolean,
        allowedThisSession: Boolean
    ): Boolean = wifiOnly && !allowMobileData && isMetered && !allowedThisSession

    /**
     * Whether a transfer nobody is being asked about may use a metered connection.
     *
     * The yes given in the dialog covers the rest of the round, not only the app it was asked
     * about: a queue moves one item at a time, and answering "yes, on mobile" for the first of five
     * downloads only to have the other four stop would be a strange reading of it.
     */
    fun allowsMobileData(allowMobileData: Boolean, allowedThisSession: Boolean): Boolean =
        allowMobileData || allowedThisSession
}

/**
 * The answer to [MeteredDownloadPolicy], for as long as the app is running.
 *
 * Deliberately not persisted. Saying yes to one download is not a setting, and being asked once per
 * session is the difference between a warning and a nuisance; the checkbox in the dialog is there
 * for the person who wants it to be a setting.
 */
object MeteredDownloadConsent {

    @Volatile
    private var allowed = false

    fun isAllowedThisSession(): Boolean = allowed

    fun allowForThisSession() {
        allowed = true
    }

    /** Used when the setting itself changes, so a stale yes cannot outlive the reason for it. */
    fun forget() {
        allowed = false
    }
}

/** Whether the connection in use bills for traffic - metered Wi-Fi counts, which is the point. */
fun isActiveNetworkMetered(context: Context): Boolean = runCatching {
    context.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered == true
}.getOrDefault(false)
