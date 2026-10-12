package dev.wystore.updates

import dev.wystore.updates.model.QueueState

/**
 * The queue's two places to be busy: one transfer at a time, and one install at a time.
 *
 * Both used to share a single slot. The download worker hands the slot to the next download the
 * moment its file is verified, while the app is still checking that file again before the install,
 * so the install was refused and waited for the last download of the round. The other way round, a
 * confirmation dialog left unanswered stopped every download behind it. Fetching a file and Android
 * installing another one do not get in each other's way; each is still one at a time.
 */
object QueueSlots {

    enum class Slot { TRANSFER, INSTALL }

    private val TRANSFER_STATES = listOf(QueueState.DOWNLOADING.name, QueueState.VERIFYING.name)
    private val INSTALL_STATES = listOf(QueueState.INSTALLING.name)

    /** Every state that holds a slot, in either. */
    val ALL_STATES: List<String> = TRANSFER_STATES + INSTALL_STATES

    fun slotOf(state: String): Slot? = when (state) {
        in TRANSFER_STATES -> Slot.TRANSFER
        in INSTALL_STATES -> Slot.INSTALL
        else -> null
    }

    fun states(slot: Slot): List<String> = when (slot) {
        Slot.TRANSFER -> TRANSFER_STATES
        Slot.INSTALL -> INSTALL_STATES
    }

    /** The states a row moving to [state] would have to share with; empty when it takes no slot. */
    fun statesInSlotOf(state: String): List<String> = slotOf(state)?.let(::states).orEmpty()

    /** Whether moving from [from] to [to] takes a slot the row did not already hold. */
    fun entersSlot(from: String, to: String): Boolean {
        val target = slotOf(to) ?: return false
        return slotOf(from) != target
    }
}
