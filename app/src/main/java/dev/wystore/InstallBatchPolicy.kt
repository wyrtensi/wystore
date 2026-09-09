package dev.wystore

/**
 * The bookkeeping behind "update all".
 *
 * Android asks for confirmation once per app, so the batch installs one at a time and waits for the
 * current one to settle. Deciding that from the queue rather than from an install callback is what
 * makes it work at all: session installs report to a BroadcastReceiver, so the Activity's own
 * callback never fires and the batch used to stop after the first app.
 */
object InstallBatchPolicy {

    /**
     * Whether the app the batch is waiting on has finished, however it finished.
     *
     * [handedOver] says whether Android has already been given the APK. It is the difference
     * between "downloaded, the dialog is about to appear" and "downloaded again, because the user
     * said no" — both of which look identical in the queue, since a declined install goes back to
     * waiting rather than to an error.
     */
    fun isSettled(statuses: List<InstallQueueStatus>, handedOver: Boolean): Boolean = when {
        statuses.any { it == InstallQueueStatus.INSTALLING } -> false
        handedOver -> true
        // Never reached the installer and is no longer waiting for it: nothing more will happen.
        else -> statuses.none { it == InstallQueueStatus.READY }
    }

    /** Whether the app has reached Android's installer, which is what makes an answer possible. */
    fun isHandedOver(statuses: List<InstallQueueStatus>): Boolean =
        statuses.any { it == InstallQueueStatus.INSTALLING }

    /**
     * The next app to install: the first one still waiting for it. Anything that left the list in
     * the meantime — installed from its own card, cancelled, cleaned up — is skipped rather than
     * stalling everything behind it.
     */
    fun next(remaining: List<String>, stillPending: Set<String>): String? =
        remaining.firstOrNull { it in stillPending }

    /**
     * The packages an "install as soon as it is downloaded" run should add to the batch.
     *
     * Auto-install used to hand the first package straight to the Activity and drop the rest into
     * the batch list without ever starting a batch, so nothing advanced them: adopting a dozen apps
     * at once produced exactly one confirmation dialog and eleven downloads nobody was ever asked
     * about. It goes through the same list "update all" uses instead.
     *
     * Only what was asked for, only what has finished downloading, and nothing the batch is already
     * carrying - this is consulted on every queue change, and a package must not be enrolled twice.
     */
    fun autoInstallAdditions(
        requested: Set<String>,
        pending: List<String>,
        alreadyQueued: List<String>,
        current: String?
    ): List<String> = pending.filter { it in requested && it !in alreadyQueued && it != current }

    /** What is left after [current] is taken; the apps skipped over it are dropped with it. */
    fun remainingAfter(remaining: List<String>, current: String?): List<String> {
        if (current == null) return emptyList()
        val index = remaining.indexOf(current)
        if (index < 0) return remaining
        return remaining.drop(index + 1)
    }
}
