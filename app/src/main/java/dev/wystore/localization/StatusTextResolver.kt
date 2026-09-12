package dev.wystore.localization

import android.content.Context
import androidx.annotation.StringRes
import dev.wystore.R
import dev.wystore.data.VerificationError
import dev.wystore.updates.QueueErrorStrings
import dev.wystore.updates.model.QueueErrorCode
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
        StatusCode.NOT_INSTALLED -> R.string.status_not_installed
        StatusCode.QUEUED -> R.string.status_queued
        StatusCode.DOWNLOADING -> R.string.status_downloading
        StatusCode.PAUSED -> R.string.status_paused
        StatusCode.VERIFYING -> R.string.status_verifying
        StatusCode.READY_TO_INSTALL -> R.string.status_ready_to_install
        StatusCode.AWAITING_UNKNOWN_SOURCES_PERMISSION -> R.string.status_awaiting_permission
        StatusCode.AWAITING_USER_CONFIRMATION -> R.string.status_awaiting_confirmation
        StatusCode.INSTALLING -> R.string.status_installing
        StatusCode.INSTALLED -> R.string.status_installed
        StatusCode.FAILED_NETWORK -> R.string.status_failed_network
        StatusCode.FAILED_STORAGE -> R.string.status_failed_storage
        StatusCode.FAILED_SIGNATURE -> R.string.status_failed_signature
        StatusCode.FAILED_SOURCE_UNCONFIRMED -> R.string.unverified_source_status
        StatusCode.FAILED_GENERIC -> R.string.status_failed_generic
        StatusCode.CANCELLED -> R.string.status_cancelled
        StatusCode.SKIPPED -> R.string.status_skipped
        StatusCode.OFFER_NEXT -> R.string.status_offer_next
    }

    /**
     * The resource for a failed row: the verification reason when the row names one, then the
     * queue's own code, and the plain sentence when it carries neither.
     *
     * A failure used to be announced with the detail the data layer recorded - "Action is not
     * allowed while queue item ... is DOWNLOADING." and "SOURCE_FINGERPRINT_MISMATCH" both reached
     * the screen that way. Both codes are typed and both have had a sentence all along; this is
     * the same ladder the queue screen already climbs.
     */
    @StringRes
    fun failureStringRes(message: StatusMessage): Int {
        // The question comes before the diagnosis: a row the user can answer for says what it is
        // asking, not which check refused the file.
        if (message.code == StatusCode.FAILED_SOURCE_UNCONFIRMED) return stringRes(message.code)
        message.args[StatusMessage.ARG_VERIFICATION_ERROR]
            ?.let { name -> VerificationError.entries.firstOrNull { it.name == name } }
            ?.let { return VerificationTextResolver.stringRes(it) }
        message.args[StatusMessage.ARG_ERROR_CODE]
            ?.let { name -> QueueErrorCode.entries.firstOrNull { it.name == name } }
            ?.let { return QueueErrorStrings.stringRes(it) }
        return stringRes(message.code)
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
        StatusCode.FAILED_NETWORK,
        StatusCode.FAILED_STORAGE,
        StatusCode.FAILED_SIGNATURE,
        StatusCode.FAILED_SOURCE_UNCONFIRMED,
        StatusCode.FAILED_GENERIC -> context.getString(failureStringRes(message))
        else -> context.getString(stringRes(message.code))
    }
}
