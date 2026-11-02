package dev.wystore.updates

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Durable sessions outlive a process; this map distinguishes them from its currently running calls. */
object InstallAttemptTracker {
    private val active = ConcurrentHashMap<String, String>()
    fun begin(queueId: String, token: String = UUID.randomUUID().toString()): String? =
        if (active.putIfAbsent(queueId, token) == null) token else null
    fun current(queueId: String): String? = active[queueId]
    fun isActive(queueId: String, token: String): Boolean = active[queueId] == token
    fun end(queueId: String, token: String) { active.remove(queueId, token) }
}
