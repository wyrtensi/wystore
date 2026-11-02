package dev.wystore.background

enum class PauseNotificationAction { KEEP, CANCEL, PUBLISH }

/** A pause is an incident, not a queue summary to repost after every device scan. */
object GooglePlayPauseNotificationPolicy {
    fun decide(enabled: Boolean, packages: Set<String>, notified: Set<String>): PauseNotificationAction = when {
        !enabled || packages.isEmpty() -> PauseNotificationAction.CANCEL
        packages.any { it !in notified } -> PauseNotificationAction.PUBLISH
        else -> PauseNotificationAction.KEEP
    }
}
