package dev.wystore.updates

/**
 * What to do with a row that still says INSTALLING when the app comes back to the front.
 *
 * A confirmation dialog left with Home, or swiped out of recents, never reports back, so the row
 * stayed INSTALLING. INSTALLING holds the queue's single slot, and every download after it found
 * the slot taken and quietly did nothing - "Download now" included - until the phone rebooted.
 *
 * Only a row whose install is known to be waiting for the user is judged: Android asked for the
 * dialog ([awaitingUserSince]) and the app, not the dialog, is now in front. Android keeps such a
 * session "active" the whole time the dialog waits, so that flag cannot tell a waiting dialog from
 * a running install. A short grace covers an answer given a moment ago whose result has not landed.
 * Installs nobody was asked about - root, background, the legacy installer - are left alone.
 */
object StuckInstallPolicy {
    const val GRACE_MILLIS = 60 * 1000L

    enum class Outcome { LEAVE, SUCCEEDED, RETURN_TO_READY }

    fun decide(
        expectedVersionCode: Long,
        awaitingUserSince: Long?,
        now: Long,
        installedVersionCode: Long?
    ): Outcome = when {
        awaitingUserSince == null -> Outcome.LEAVE
        now - awaitingUserSince < GRACE_MILLIS -> Outcome.LEAVE
        installedVersionCode != null && installedVersionCode >= expectedVersionCode -> Outcome.SUCCEEDED
        else -> Outcome.RETURN_TO_READY
    }
}
