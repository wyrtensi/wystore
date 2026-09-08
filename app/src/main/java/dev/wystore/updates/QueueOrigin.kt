package dev.wystore.updates

/**
 * Who put a row in the queue.
 *
 * A check that found an update and a button someone pressed produce the same kind of row, and the
 * queue has always told them apart by priority - a user's request goes ahead of background work.
 * That distinction decides more than order: an update nobody asked for has nothing to do when the
 * file turns out to be no newer than what is installed, while a reinstall the user asked for is
 * precisely a file that is not newer. This gives the number a name instead of leaving it to be
 * recognised on sight.
 */
object QueueOrigin {

    const val USER_REQUESTED_PRIORITY = 10

    fun isUserRequested(priority: Int): Boolean = priority >= USER_REQUESTED_PRIORITY
}
