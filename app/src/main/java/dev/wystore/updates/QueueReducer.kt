package dev.wystore.updates

import dev.wystore.updates.model.QueueAction
import dev.wystore.updates.model.QueueItemSnapshot
import dev.wystore.updates.model.QueueMode
import dev.wystore.updates.model.QueueState

object QueueReducer {
    fun reduce(current: QueueItemSnapshot, action: QueueAction): QueueItemSnapshot = when (action) {
        QueueAction.StartChecking -> transition(current, QueueState.AVAILABLE, QueueState.CHECKING)
        QueueAction.StartDownload -> transition(
            current,
            setOf(QueueState.AVAILABLE, QueueState.CHECKING),
            QueueState.DOWNLOADING
        )
        is QueueAction.DownloadProgress -> updateProgress(current, action)
        QueueAction.StartVerification -> transition(current, QueueState.DOWNLOADING, QueueState.VERIFYING)
        QueueAction.Verified -> transition(current, QueueState.VERIFYING, QueueState.READY_TO_INSTALL)
        QueueAction.AwaitUnknownSourcesPermission -> transition(
            current,
            QueueState.READY_TO_INSTALL,
            QueueState.AWAITING_UNKNOWN_SOURCES_PERMISSION
        )
        QueueAction.UnknownSourcesPermissionGranted -> transition(
            current,
            QueueState.AWAITING_UNKNOWN_SOURCES_PERMISSION,
            QueueState.AWAITING_USER_CONFIRMATION
        )
        QueueAction.RequestInstallConfirmation -> transition(
            current,
            QueueState.READY_TO_INSTALL,
            QueueState.AWAITING_USER_CONFIRMATION
        )
        QueueAction.StartInstall -> transition(
            current,
            QueueState.AWAITING_USER_CONFIRMATION,
            QueueState.INSTALLING
        )
        QueueAction.InstallSucceeded -> transition(current, QueueState.INSTALLING, QueueState.OFFER_NEXT)
        is QueueAction.InstallFailed -> installFail(current, action)
        is QueueAction.Failed -> fail(current, action.errorCode, action.errorDetail)
        QueueAction.Skip -> skip(current)
        QueueAction.Cancel -> cancel(current)
        QueueAction.Retry -> retry(current)
        QueueAction.OfferNext -> transition(
            current,
            setOf(QueueState.SKIPPED, QueueState.OFFER_NEXT),
            QueueState.OFFER_NEXT
        )
    }

    fun order(items: List<QueueItemSnapshot>): List<QueueItemSnapshot> = items.sortedWith(
        compareByDescending<QueueItemSnapshot> { it.priority }
            .thenBy { it.position }
            .thenBy { it.packageName }
            .thenBy { it.id }
    )

    fun automaticNextAction(mode: QueueMode, current: QueueItemSnapshot): QueueAction? = when {
        mode == QueueMode.SMART_PROMPTS && current.state in setOf(QueueState.OFFER_NEXT, QueueState.SKIPPED) -> QueueAction.OfferNext
        else -> null
    }

    private fun updateProgress(
        current: QueueItemSnapshot,
        action: QueueAction.DownloadProgress
    ): QueueItemSnapshot {
        requireState(current, QueueState.DOWNLOADING)
        require(action.downloadedBytes >= 0) { "Downloaded bytes cannot be negative." }
        require(action.totalBytes >= 0) { "Total bytes cannot be negative." }
        require(action.totalBytes == 0L || action.downloadedBytes <= action.totalBytes) {
            "Downloaded bytes cannot exceed total bytes."
        }
        return current.copy(
            downloadedBytes = action.downloadedBytes,
            totalBytes = action.totalBytes,
            errorCode = null,
            errorDetail = null
        )
    }

    private fun fail(
        current: QueueItemSnapshot,
        errorCode: dev.wystore.updates.model.QueueErrorCode,
        errorDetail: String?
    ): QueueItemSnapshot {
        requireState(
            current,
            setOf(
                QueueState.CHECKING,
                QueueState.DOWNLOADING,
                QueueState.VERIFYING,
                QueueState.READY_TO_INSTALL,
                QueueState.AWAITING_UNKNOWN_SOURCES_PERMISSION,
                QueueState.AWAITING_USER_CONFIRMATION
            )
        )
        return current.copy(
            state = QueueState.FAILED,
            errorCode = errorCode,
            errorDetail = errorDetail
        )
    }

    private fun installFail(
        current: QueueItemSnapshot,
        action: QueueAction.InstallFailed
    ): QueueItemSnapshot {
        requireState(current, QueueState.INSTALLING)
        return current.copy(
            state = QueueState.FAILED,
            errorCode = action.errorCode,
            errorDetail = action.errorDetail
        )
    }

    private fun skip(current: QueueItemSnapshot): QueueItemSnapshot {
        requireState(
            current,
            setOf(
                QueueState.AVAILABLE,
                QueueState.CHECKING,
                QueueState.DOWNLOADING,
                QueueState.VERIFYING,
                QueueState.READY_TO_INSTALL,
                QueueState.AWAITING_UNKNOWN_SOURCES_PERMISSION,
                QueueState.AWAITING_USER_CONFIRMATION
            )
        )
        return current.copy(state = QueueState.SKIPPED, errorCode = null, errorDetail = null)
    }

    private fun cancel(current: QueueItemSnapshot): QueueItemSnapshot {
        requireState(
            current,
            setOf(
                QueueState.CHECKING,
                QueueState.DOWNLOADING,
                QueueState.VERIFYING,
                QueueState.READY_TO_INSTALL,
                QueueState.AWAITING_UNKNOWN_SOURCES_PERMISSION,
                QueueState.AWAITING_USER_CONFIRMATION
            )
        )
        return current.copy(state = QueueState.CANCELED, errorCode = null, errorDetail = null)
    }

    private fun retry(current: QueueItemSnapshot): QueueItemSnapshot {
        requireState(current, setOf(QueueState.FAILED, QueueState.CANCELED))
        return current.copy(
            state = QueueState.AVAILABLE,
            downloadedBytes = 0,
            totalBytes = 0,
            errorCode = null,
            errorDetail = null
        )
    }

    private fun transition(
        current: QueueItemSnapshot,
        expected: QueueState,
        target: QueueState
    ): QueueItemSnapshot {
        requireState(current, expected)
        return current.copy(state = target, errorCode = null, errorDetail = null)
    }

    private fun transition(
        current: QueueItemSnapshot,
        expected: Set<QueueState>,
        target: QueueState
    ): QueueItemSnapshot {
        requireState(current, expected)
        return current.copy(state = target, errorCode = null, errorDetail = null)
    }

    private fun requireState(current: QueueItemSnapshot, expected: QueueState) {
        requireState(current, setOf(expected))
    }

    private fun requireState(current: QueueItemSnapshot, expected: Set<QueueState>) {
        check(current.state in expected) {
            "Action is not allowed while queue item ${current.id} is ${current.state}."
        }
    }
}
