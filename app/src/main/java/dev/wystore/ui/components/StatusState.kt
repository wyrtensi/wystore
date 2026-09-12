package dev.wystore.ui.components

enum class StatusCode {
    CHECKING,
    NOT_INSTALLED,
    QUEUED,
    DOWNLOADING,
    PAUSED,
    VERIFYING,
    READY_TO_INSTALL,
    AWAITING_UNKNOWN_SOURCES_PERMISSION,
    AWAITING_USER_CONFIRMATION,
    INSTALLING,
    INSTALLED,
    FAILED_NETWORK,
    FAILED_STORAGE,
    FAILED_SIGNATURE,

    /**
     * The source's own metadata disagrees with the file it served, and the user is being asked
     * whether to take it anyway. A question rather than a verdict: see
     * [dev.wystore.updates.UnverifiedSourceConsent].
     */
    FAILED_SOURCE_UNCONFIRMED,
    FAILED_GENERIC,
    CANCELLED,
    SKIPPED,
    OFFER_NEXT
}

data class StatusMessage(
    val code: StatusCode,
    val args: Map<String, String> = emptyMap()
) {
    companion object {
        /**
         * What a failed row carries: the names of the codes it failed with, never the text.
         *
         * The detail a row records is an exception message from the data layer, in whatever
         * language it was written in, and it used to be shown to the user as it was. Only these
         * two travel to the screen now; the detail itself stays in the diagnostics report.
         */
        const val ARG_ERROR_CODE = "errorCode"
        const val ARG_VERIFICATION_ERROR = "verificationError"
    }
}
