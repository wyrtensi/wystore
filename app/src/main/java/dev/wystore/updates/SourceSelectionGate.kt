package dev.wystore.updates

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Serialize a source change with the durable claim that an installation has begun. */
internal object SourceSelectionGate {
    private val mutex = Mutex()
    suspend fun <T> locked(action: suspend () -> T): T = mutex.withLock { action() }
    suspend fun <T> commit(action: suspend () -> T): T = locked {
        currentCoroutineContext().ensureActive()
        withContext(NonCancellable) { action() }
    }
}
