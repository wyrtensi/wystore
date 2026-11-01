package dev.wystore.updates

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serialize a source change with the durable claim that an installation has begun. */
internal object SourceSelectionGate {
    private val mutex = Mutex()
    suspend fun <T> locked(action: suspend () -> T): T = mutex.withLock { action() }
}
