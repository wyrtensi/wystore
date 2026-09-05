package dev.wystore.updates

import dev.wystore.updates.model.QueueAction
import dev.wystore.updates.model.QueueErrorCode
import dev.wystore.updates.model.QueueItemSnapshot
import dev.wystore.updates.model.QueueState

object InstallReconciliationPolicy {
    const val STALE_INSTALLING_TIMEOUT_MILLIS = 15 * 60 * 1000L // 15 minutes

    enum class CallbackResult {
        SUCCESS,
        CANCELLED,
        FAILED
    }

    fun reconcileCallback(
        result: CallbackResult,
        statusMessage: String? = null
    ): QueueAction {
        return when (result) {
            CallbackResult.SUCCESS -> QueueAction.InstallSucceeded
            CallbackResult.CANCELLED -> QueueAction.Cancel
            CallbackResult.FAILED -> QueueAction.InstallFailed(
                errorCode = QueueErrorCode.INSTALL_FAILED,
                // The typed code is what the UI renders; this stays the raw technical detail.
                errorDetail = statusMessage ?: "PackageInstaller reported failure"
            )
        }
    }

    fun reconcileObservedPackageChange(
        item: QueueItemSnapshot,
        newVersionCode: Long
    ): QueueAction? {
        return if (item.state == QueueState.INSTALLING && newVersionCode >= item.versionCode) {
            QueueAction.InstallSucceeded
        } else {
            null
        }
    }

    fun isStaleInstalling(
        item: QueueItemSnapshot,
        updatedAt: Long,
        nowMillis: Long
    ): Boolean {
        return item.state == QueueState.INSTALLING && (nowMillis - updatedAt) > STALE_INSTALLING_TIMEOUT_MILLIS
    }

    fun reconcileStaleInstalling(
        item: QueueItemSnapshot,
        installedVersionCode: Long?
    ): QueueAction {
        return if (installedVersionCode != null && installedVersionCode >= item.versionCode) {
            QueueAction.InstallSucceeded
        } else {
            QueueAction.InstallFailed(
                errorCode = QueueErrorCode.TIMEOUT,
                errorDetail = "Install confirmation timed out"
            )
        }
    }

    fun checkSignatureCompatibility(
        installedDigests: Set<String>,
        updateDigests: Set<String>
    ): Boolean {
        if (installedDigests.isEmpty()) return true
        return installedDigests == updateDigests
    }
}
