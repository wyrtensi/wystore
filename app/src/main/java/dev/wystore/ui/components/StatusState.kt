package dev.wystore.ui.components

enum class StatusCode {
    CHECKING,
    NOT_INSTALLED,
    QUEUED,
    DOWNLOADING,
    VERIFYING,
    READY_TO_INSTALL,
    AWAITING_UNKNOWN_SOURCES_PERMISSION,
    AWAITING_USER_CONFIRMATION,
    INSTALLING,
    INSTALLED,
    FAILED_NETWORK,
    FAILED_STORAGE,
    FAILED_SIGNATURE,
    FAILED_GENERIC,
    CANCELLED,
    SKIPPED,
    OFFER_NEXT
}

data class StatusMessage(
    val code: StatusCode,
    val args: Map<String, String> = emptyMap()
)
