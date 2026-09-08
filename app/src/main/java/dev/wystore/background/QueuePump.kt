package dev.wystore.background

import android.content.Context
import dev.wystore.data.EventLog
import dev.wystore.updates.QueueRepository

/**
 * Passes the turn to whatever is queued behind.
 *
 * The queue moves one item at a time, so something has to start the next one when the current
 * transfer or install lets go. Nothing did: a second app added while the first was working stayed
 * at AVAILABLE until the user found the "start" button - and before that it was refused outright,
 * reported as "Wy Store itself failed" over a queue behaving exactly as designed.
 *
 * Safe to call from anywhere a slot is released, including twice: it starts nothing while an item
 * is still active, and nothing when the queue is empty.
 */
object QueuePump {

    suspend fun startNext(context: Context) {
        val repository = QueueRepository.getInstance(context.applicationContext)
        val next = runCatching {
            if (repository.activeIds().isNotEmpty()) return
            repository.nextEligible()
        }.getOrNull() ?: return
        runCatching { TransferDispatcher.dispatch(context.applicationContext, next.id) }
            .onFailure { error ->
                // If the turn cannot be passed on, the whole queue stops here. Written down, since
                // from the outside it is indistinguishable from a queue with nothing left to do.
                runCatching {
                    EventLog(context).record(
                        packageName = next.packageName,
                        code = "DISPATCH_FAILED",
                        detail = error.message ?: error::class.java.simpleName
                    )
                }
            }
    }
}
