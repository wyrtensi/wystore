package dev.wystore.updates.model

import dev.wystore.data.ManagedSource

enum class QueueMode {
    SMART_PROMPTS,
    MANUAL_ONE_BY_ONE
}

enum class QueueState {
    AVAILABLE,
    CHECKING,
    DOWNLOADING,
    VERIFYING,
    READY_TO_INSTALL,
    AWAITING_UNKNOWN_SOURCES_PERMISSION,
    AWAITING_USER_CONFIRMATION,
    INSTALLING,
    INSTALLED,
    OFFER_NEXT,
    SKIPPED,
    CANCELED,
    FAILED
}

enum class QueueErrorCode {
    NETWORK,
    RATE_LIMITED,
    SOURCE_CHANGED,
    STORAGE_FULL,
    INTEGRITY,
    SIGNATURE,
    INCOMPATIBLE,
    PERMISSION,
    INSTALL_CANCELED,
    INSTALL_FAILED,
    ARTIFACT_MISSING,
    TIMEOUT,

    /**
     * The app failed, not the network or the source. Reporting these as NETWORK sent users chasing
     * their connection over a bug: an illegal queue transition was shown as "network error".
     */
    INTERNAL
}

data class QueueItemSnapshot(
    val id: String,
    val packageName: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val source: ManagedSource,
    val state: QueueState,
    val priority: Int,
    val position: Int,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val errorCode: QueueErrorCode? = null,
    val errorDetail: String? = null
)

sealed interface QueueAction {
    data object StartChecking : QueueAction

    data object StartDownload : QueueAction

    data class DownloadProgress(
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : QueueAction

    data object StartVerification : QueueAction

    data object Verified : QueueAction

    data object AwaitUnknownSourcesPermission : QueueAction

    data object UnknownSourcesPermissionGranted : QueueAction

    data object RequestInstallConfirmation : QueueAction

    data object StartInstall : QueueAction

    data object InstallSucceeded : QueueAction

    data class InstallFailed(
        val errorCode: QueueErrorCode = QueueErrorCode.INSTALL_FAILED,
        val errorDetail: String? = null
    ) : QueueAction

    data class Failed(
        val errorCode: QueueErrorCode,
        val errorDetail: String? = null
    ) : QueueAction

    data object Skip : QueueAction

    data object Cancel : QueueAction

    data object Retry : QueueAction

    data object OfferNext : QueueAction
}
