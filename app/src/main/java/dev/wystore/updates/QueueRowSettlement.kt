package dev.wystore.updates

import dev.wystore.updates.model.QueueState

/** Which rows of a package an install has made pointless - see [QueueRepository.removeSettled]. */
object QueueRowSettlement {
    private val IN_FLIGHT = setOf(
        QueueState.CHECKING.name,
        QueueState.DOWNLOADING.name,
        QueueState.VERIFYING.name,
        QueueState.INSTALLING.name
    )

    fun isSettled(state: String, rowVersionCode: Long, installedVersionCode: Long): Boolean =
        state !in IN_FLIGHT && rowVersionCode <= installedVersionCode
}
