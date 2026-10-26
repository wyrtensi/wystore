package dev.wystore.updates

import android.content.Context
import dev.wystore.background.TransferDispatcher
import dev.wystore.updates.model.QueueState

enum class PreparedInstallAction { DOWNLOAD, OFFER_CONFIRMATION, KEEP_ACTIVE }

/** Routes an adopted queue row without asking a downloaded APK to go through the network again. */
object PreparedInstallDispatcher {
    fun action(state: QueueState, requiresUpdateOwnership: Boolean): PreparedInstallAction {
        if (!requiresUpdateOwnership) return PreparedInstallAction.DOWNLOAD
        return when (state) {
            QueueState.AVAILABLE -> PreparedInstallAction.DOWNLOAD
            QueueState.READY_TO_INSTALL, QueueState.AWAITING_UNKNOWN_SOURCES_PERMISSION ->
                PreparedInstallAction.OFFER_CONFIRMATION
            QueueState.CHECKING, QueueState.DOWNLOADING, QueueState.VERIFYING,
            QueueState.AWAITING_USER_CONFIRMATION, QueueState.INSTALLING -> PreparedInstallAction.KEEP_ACTIVE
            else -> throw IllegalStateException("Replacement queue item is not prepared: $state")
        }
    }

    suspend fun dispatch(context: Context, queueId: String) {
        val item = QueueRepository.getInstance(context).getEntityById(queueId)
            ?: throw IllegalStateException("Replacement queue item is missing: $queueId")
        when (action(QueueState.valueOf(item.state), item.requiresUpdateOwnership)) {
            PreparedInstallAction.DOWNLOAD -> TransferDispatcher.dispatch(context, queueId)
            PreparedInstallAction.OFFER_CONFIRMATION -> AutoInstallStore(context).request(item.packageName)
            PreparedInstallAction.KEEP_ACTIVE -> Unit
        }
    }
}
