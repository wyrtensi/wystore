package dev.wystore.background

import android.content.Context

/**
 * How many times a failed transfer may ask to be run again.
 *
 * [UpdateDownloadWorker] has always had a cap, because WorkManager hands it a run count. The
 * user-initiated job path - the one Android 14 and later actually use - had none: it passed the
 * "retryable" flag straight to jobFinished, so a download that could not succeed rescheduled
 * itself for ever. Each attempt woke the device, took a wake lock and gave up in milliseconds,
 * and JobScheduler's backoff spread them across the whole night.
 */
object TransferRetryPolicy {

    const val MAX_ATTEMPTS = UpdateDownloadWorker.MAX_RUN_ATTEMPTS

    fun shouldReschedule(retryable: Boolean, attempts: Int): Boolean =
        retryable && attempts < MAX_ATTEMPTS
}

/**
 * Counts what a JobParameters cannot tell us.
 *
 * JobScheduler carries no run count, and the process that would hold one in memory is exactly the
 * process that dies between attempts. Kept in the same preferences file as the queue's other
 * derived state.
 */
class TransferAttemptStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("wystore_queue", Context.MODE_PRIVATE)

    /** Records one more attempt and returns how many there have now been. */
    fun record(queueId: String): Int {
        val next = attempts(queueId) + 1
        // Committed rather than applied: the job's process can end the moment it returns.
        prefs.edit().putInt(key(queueId), next).commit()
        return next
    }

    fun attempts(queueId: String): Int = prefs.getInt(key(queueId), 0)

    fun clear(queueId: String) {
        prefs.edit().remove(key(queueId)).commit()
    }

    private fun key(queueId: String) = "$KEY_PREFIX$queueId"

    private companion object {
        const val KEY_PREFIX = "transfer_attempts:"
    }
}
