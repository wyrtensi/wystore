package dev.wystore.data

/** A late catalogue response must never authorize deletion after its prompt was dismissed. */
class AdoptionRequestTracker {
    private var generation = 0L

    fun begin(): Long = ++generation
    fun invalidate() { generation++ }
    fun isCurrent(request: Long): Boolean = request == generation
}
