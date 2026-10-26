package dev.wystore.updates

import dev.wystore.updates.model.QueueState
import dev.wystore.updates.model.QueueErrorCode

/** Which rows of a package an install has made pointless - see [QueueRepository.removeSettled]. */
object QueueRowSettlement {
    private val IN_FLIGHT = setOf(
        QueueState.CHECKING.name,
        QueueState.DOWNLOADING.name,
        QueueState.VERIFYING.name,
        QueueState.INSTALLING.name
    )

    fun isSettled(
        state: String,
        rowVersionCode: Long,
        installedVersionCode: Long,
        errorCode: String? = null
    ): Boolean = errorCode != QueueErrorCode.UPDATE_OWNERSHIP_UNCONFIRMED.name &&
        state !in IN_FLIGHT && rowVersionCode <= installedVersionCode
}
