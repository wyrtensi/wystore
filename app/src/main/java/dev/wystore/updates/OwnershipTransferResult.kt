package dev.wystore.updates

import dev.wystore.updates.model.QueueErrorCode
import dev.wystore.updates.model.QueueItemSnapshot
import dev.wystore.updates.model.QueueState

/** Installation succeeded independently of whether Android granted the requested update ownership. */
object OwnershipTransferResult {
    fun successfulInstall(
        current: QueueItemSnapshot,
        sdkInt: Int,
        wyStorePackageName: String,
        updateOwnerPackageName: String?
    ): QueueItemSnapshot {
        // The package broadcast and session callback describe the same install. Its first result
        // stays visible even if a duplicate callback arrives with different ownership information.
        if (current.state == QueueState.INSTALLED) return current
        val unconfirmed = current.requiresUpdateOwnership &&
            (sdkInt < 34 || updateOwnerPackageName != wyStorePackageName)
        return current.copy(
            state = QueueState.INSTALLED,
            errorCode = if (unconfirmed) QueueErrorCode.UPDATE_OWNERSHIP_UNCONFIRMED else null,
            errorDetail = if (unconfirmed) {
                "Update ownership unconfirmed: Android API $sdkInt, owner=${updateOwnerPackageName ?: "unknown"}"
            } else null
        )
    }
}
