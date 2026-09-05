package dev.wystore.localization

import android.content.Context
import androidx.annotation.StringRes
import dev.wystore.R
import dev.wystore.ui.components.StatusCode
import dev.wystore.ui.components.StatusMessage

/**
 * Turns a [StatusMessage] into the line a row shows about what is happening to it.
 *
 * This used to hold a Russian and an English sentence side by side in Kotlin and pick between them
 * from the device locale, which meant the app's own language setting did not reach it and a third
 * language would have needed code. It reads resources now, like everything else.
 */
object StatusTextResolver {

    /**
     * The resource for a code on its own. Separate from [resolve] so the mapping can be checked
     * without a context; the argument-bearing states are formatted in [resolve].
     */
    @StringRes
    fun stringRes(code: StatusCode): Int = when (code) {
        StatusCode.CHECKING -> R.string.status_checking
        StatusCode.QUEUED -> R.string.status_queued
        StatusCode.DOWNLOADING -> R.string.status_downloading
        StatusCode.VERIFYING -> R.string.status_verifying
        StatusCode.READY_TO_INSTALL -> R.string.status_ready_to_install
        StatusCode.AWAITING_UNKNOWN_SOURCES_PERMISSION -> R.string.status_awaiting_permission
        StatusCode.AWAITING_USER_CONFIRMATION -> R.string.status_awaiting_confirmation
        StatusCode.INSTALLING -> R.string.status_installing
        StatusCode.INSTALLED -> R.string.status_installed
        StatusCode.FAILED_NETWORK -> R.string.status_failed_network
        StatusCode.FAILED_STORAGE -> R.string.status_failed_storage
        StatusCode.FAILED_SIGNATURE -> R.string.status_failed_signature
        StatusCode.FAILED_GENERIC -> R.string.status_failed_generic
        StatusCode.CANCELLED -> R.string.status_cancelled
        StatusCode.SKIPPED -> R.string.status_skipped
        StatusCode.OFFER_NEXT -> R.string.status_offer_next
    }

    fun resolve(context: Context, message: StatusMessage): String = when (message.code) {
        StatusCode.DOWNLOADING -> {
            val percent = message.args["percent"] ?: "0"
            val downloaded = message.args["downloaded"]
            val total = message.args["total"]
            if (downloaded != null && total != null) {
                context.getString(R.string.status_downloading_detail, percent, downloaded, total)
            } else {
                context.getString(R.string.status_downloading, percent)
            }
        }
        // A concrete reason from the data layer beats the generic sentence.
        StatusCode.FAILED_GENERIC -> message.args["detail"]?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.status_failed_generic)
        else -> context.getString(stringRes(message.code))
    }
}
