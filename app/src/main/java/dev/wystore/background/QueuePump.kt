package dev.wystore.background

import android.content.Context
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
        runCatching {
            val repository = QueueRepository.getInstance(context.applicationContext)
            if (repository.activeIds().isNotEmpty()) return
            val next = repository.nextEligible() ?: return
            TransferDispatcher.dispatch(context.applicationContext, next.id)
        }
    }
}
