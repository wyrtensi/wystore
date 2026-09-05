package dev.wystore.updates

import dev.wystore.updates.model.QueueState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtifactCleanupPolicyTest {

    private val policy = ArtifactCleanupPolicy(
        defaultRetentionMillis = 7 * 24 * 60 * 60 * 1000L
    )

    @Test
    fun selectsExpiredAndSupersededArtifactsWhileRetainingActiveAndCurrent() {
        val now = 1_000_000_000L
        val quota = 500_000_000L // 500 MB quota (plenty of room)

        val items = listOf(
            ArtifactCleanupCandidate(
                id = "active",
                packageName = "app.active",
                versionCode = 10,
                sizeBytes = 10_000_000L,
                state = QueueState.DOWNLOADING,
                lastAccessedAt = now
            ),
            ArtifactCleanupCandidate(
                id = "current",
                packageName = "app.current",
                versionCode = 5,
                sizeBytes = 15_000_000L,
                state = QueueState.READY_TO_INSTALL,
                lastAccessedAt = now
            ),
            ArtifactCleanupCandidate(
                id = "expired",
                packageName = "app.expired",
                versionCode = 1,
                sizeBytes = 20_000_000L,
                state = QueueState.AVAILABLE,
                lastAccessedAt = now - (8 * 24 * 60 * 60 * 1000L) // 8 days ago
            ),
            ArtifactCleanupCandidate(
                id = "superseded",
                packageName = "app.current",
                versionCode = 4, // older version of app.current
                sizeBytes = 14_000_000L,
                state = QueueState.AVAILABLE,
                lastAccessedAt = now
            )
        )

        assertEquals(setOf("expired", "superseded"), policy.select(items, now, quota).map { it.id }.toSet())
    }

    @Test
    fun selectsCanceledAndFailedArtifacts() {
        val now = 1_000_000_000L
        val quota = 500_000_000L

        val items = listOf(
            ArtifactCleanupCandidate(
                id = "canceled_item",
                packageName = "app.canceled",
                versionCode = 1,
                sizeBytes = 10_000_000L,
                state = QueueState.CANCELED,
                lastAccessedAt = now
            ),
            ArtifactCleanupCandidate(
                id = "failed_item",
                packageName = "app.failed",
                versionCode = 1,
                sizeBytes = 12_000_000L,
                state = QueueState.FAILED,
                lastAccessedAt = now
            ),
            ArtifactCleanupCandidate(
                id = "ready_item",
                packageName = "app.ready",
                versionCode = 1,
                sizeBytes = 15_000_000L,
                state = QueueState.READY_TO_INSTALL,
                lastAccessedAt = now
            )
        )

        val selectedIds = policy.select(items, now, quota).map { it.id }.toSet()
        assertTrue(selectedIds.contains("canceled_item"))
        assertTrue(selectedIds.contains("failed_item"))
        assertFalse(selectedIds.contains("ready_item"))
    }

    @Test
    fun selectsOverQuotaArtifactsInLruOrder() {
        val now = 1_000_000_000L
        val quota = 25_000_000L // 25 MB quota

        val itemOldest = ArtifactCleanupCandidate(
            id = "oldest_idle",
            packageName = "app.one",
            versionCode = 1,
            sizeBytes = 15_000_000L,
            state = QueueState.AVAILABLE,
            lastAccessedAt = now - 50_000L
        )
        val itemNewer = ArtifactCleanupCandidate(
            id = "newer_idle",
            packageName = "app.two",
            versionCode = 1,
            sizeBytes = 15_000_000L,
            state = QueueState.AVAILABLE,
            lastAccessedAt = now - 10_000L
        )

        // Total 30 MB > 25 MB quota. Oldest should be evicted first.
        val selected = policy.select(listOf(itemOldest, itemNewer), now, quota)
        assertEquals(listOf("oldest_idle"), selected.map { it.id })
    }

    @Test
    fun neverSelectsActiveArtifactsEvenIfOverQuota() {
        val now = 1_000_000_000L
        val quota = 1_000_000L // Tiny 1MB quota

        val activeItems = listOf(
            ArtifactCleanupCandidate(
                id = "downloading",
                packageName = "app.downloading",
                versionCode = 1,
                sizeBytes = 20_000_000L,
                state = QueueState.DOWNLOADING,
                lastAccessedAt = now
            ),
            ArtifactCleanupCandidate(
                id = "verifying",
                packageName = "app.verifying",
                versionCode = 1,
                sizeBytes = 20_000_000L,
                state = QueueState.VERIFYING,
                lastAccessedAt = now
            ),
            ArtifactCleanupCandidate(
                id = "installing",
                packageName = "app.installing",
                versionCode = 1,
                sizeBytes = 20_000_000L,
                state = QueueState.INSTALLING,
                lastAccessedAt = now
            )
        )

        val selected = policy.select(activeItems, now, quota)
        assertTrue(selected.isEmpty())
    }
}
