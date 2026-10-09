package dev.wystore.background

import dev.wystore.background.RunnerStopPolicy.Outcome
import dev.wystore.updates.model.QueueState
import org.junit.Assert.assertEquals
import org.junit.Test

class RunnerStopPolicyTest {

    @Test
    fun rowStoppedByNobodyGoesBackInLine() {
        // The system stopped the job: nobody relabelled the row first.
        assertEquals(Outcome.RELEASE, RunnerStopPolicy.onStopped(QueueState.DOWNLOADING))
        assertEquals(Outcome.RELEASE, RunnerStopPolicy.onStopped(QueueState.VERIFYING))
        assertEquals(Outcome.RELEASE, RunnerStopPolicy.onStopped(QueueState.CHECKING))
    }

    @Test
    fun cancelledOrSkippedRowLosesItsFiles() {
        assertEquals(Outcome.DELETE_FILES, RunnerStopPolicy.onStopped(QueueState.CANCELED))
        assertEquals(Outcome.DELETE_FILES, RunnerStopPolicy.onStopped(QueueState.SKIPPED))
    }

    @Test
    fun pausedRowKeepsEverything() {
        assertEquals(Outcome.NOTHING, RunnerStopPolicy.onStopped(QueueState.PAUSED))
        assertEquals(Outcome.NOTHING, RunnerStopPolicy.onStopped(null))
    }
}
