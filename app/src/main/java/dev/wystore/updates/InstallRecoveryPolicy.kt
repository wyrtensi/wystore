package dev.wystore.updates

object InstallRecoveryPolicy {
    enum class Decision { LEAVE, RETURN_TO_READY, ABANDON_THEN_OBSERVE, RECONCILE_CLOSED }

    fun decide(stage: String, activeAttempt: Boolean, exists: Boolean, sealed: Boolean): Decision = when {
        activeAttempt -> Decision.LEAVE
        stage == "ROOT_CLAIMED" -> Decision.RETURN_TO_READY
        !exists -> Decision.RECONCILE_CLOSED
        stage in setOf("ROOT_PREPARED", "ROOT_COMMITTED", "COMMITTED", "PREPARED") && !sealed -> Decision.ABANDON_THEN_OBSERVE
        else -> Decision.LEAVE
    }
}
