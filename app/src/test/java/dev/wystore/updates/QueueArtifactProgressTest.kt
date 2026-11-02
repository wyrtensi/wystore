package dev.wystore.updates

import dev.wystore.data.ManagedSource
import dev.wystore.data.local.UpdateQueueEntity
import dev.wystore.data.local.toSnapshot
import dev.wystore.updates.model.QueueAction
import dev.wystore.updates.model.QueueState
import org.junit.Assert.*
import org.junit.Test

class QueueArtifactProgressTest {
    @Test fun `artifact counters survive the persisted queue snapshot and reducer`() {
        val row = UpdateQueueEntity("split-progress", "org.example.app", "Example", "1", 1,
            ManagedSource.WY_STORE.name, QueueState.DOWNLOADING.name,
            downloadedBytes = 50, totalBytes = 100, artifactIndex = 2, artifactCount = 4)
        val snapshot = row.toSnapshot()
        assertEquals(2, snapshot.artifactIndex)
        assertEquals(4, snapshot.artifactCount)
        val next = QueueReducer.reduce(snapshot, QueueAction.DownloadProgress(75, 100, 3, 4))
        assertEquals(3, next.artifactIndex)
        assertEquals(4, next.artifactCount)
        assertEquals(75L, next.downloadedBytes)
    }

    @Test fun `artifact counters reject impossible positions`() {
        val row = UpdateQueueEntity("split-progress", "org.example.app", "Example", "1", 1,
            ManagedSource.WY_STORE.name, QueueState.DOWNLOADING.name).toSnapshot()
        listOf(0 to 4, 5 to 4, 1 to 0).forEach { (index, count) ->
            assertThrows(IllegalArgumentException::class.java) {
                QueueReducer.reduce(row, QueueAction.DownloadProgress(1, 100, index, count))
            }
        }
    }
}
