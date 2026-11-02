package dev.wystore.updates

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class SourceSelectionGateTest {
    @Test fun `dismissal cannot interrupt the durable part of a source revision change`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val mutations = mutableListOf<Int>()
        val job = launch {
            SourceSelectionGate.commit {
                mutations += 1
                entered.complete(Unit)
                finish.await()
                mutations += 2
            }
        }
        entered.await()
        job.cancel()
        finish.complete(Unit)
        job.join()
        assertEquals(listOf(1, 2), mutations)
        SourceSelectionGate.locked { assertTrue(job.isCancelled) }
    }

    @Test fun `a dismissed request waiting for the gate never starts a commit`() = runBlocking {
        var committed = false
        SourceSelectionGate.locked {
            val started = CompletableDeferred<Unit>()
            val job = launch { started.complete(Unit); SourceSelectionGate.commit { committed = true } }
            started.await()
            yield()
            job.cancelAndJoin()
        }
        assertFalse(committed)
    }
}
