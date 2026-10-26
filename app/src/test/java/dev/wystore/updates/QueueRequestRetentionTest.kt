package dev.wystore.updates

import dev.wystore.data.local.UpdateQueueEntity
import dev.wystore.updates.model.QueueState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueRequestRetentionTest {
    private val stale = UpdateQueueEntity(
        id = "row", packageName = "example.app", label = "Source label", versionName = "2.0",
        versionCode = 20, source = "RUSTORE", state = QueueState.AVAILABLE.name
    )

    @Test
    fun `an ownership request promotes an automatic download without losing its progress`() {
        val automatic = stale.copy(state = QueueState.DOWNLOADING.name, downloadedBytes = 1024, priority = 0)
        val requested = QueueRequestRetention.merge(automatic.copy(requiresUpdateOwnership = true), automatic)
        assertEquals(QueueOrigin.USER_REQUESTED_PRIORITY, requested.priority)
        assertTrue(requested.requiresUpdateOwnership)
        assertEquals(QueueState.DOWNLOADING.name, requested.state)
        assertEquals(1024L, requested.downloadedBytes)
    }

    @Test
    fun `an ownership request promotes a ready automatic APK without resetting verification`() {
        val automatic = stale.copy(state = QueueState.READY_TO_INSTALL.name, signingDigests = "verified", priority = 0)
        val requested = QueueRequestRetention.merge(automatic.copy(requiresUpdateOwnership = true), automatic)
        assertEquals(QueueOrigin.USER_REQUESTED_PRIORITY, requested.priority)
        assertTrue(requested.requiresUpdateOwnership)
        assertEquals(QueueState.READY_TO_INSTALL.name, requested.state)
        assertEquals("verified", requested.signingDigests)
    }

    @Test
    fun `a stale source snapshot retains the manual request recorded meanwhile`() {
        val saved = stale.copy(priority = QueueOrigin.USER_REQUESTED_PRIORITY)
        val merged = QueueRequestRetention.merge(stale.copy(label = "New source label"), saved)
        assertEquals(QueueOrigin.USER_REQUESTED_PRIORITY, merged.priority)
        assertEquals("New source label", merged.label)
    }

    @Test
    fun `replacement of a conflicting row retains its manual ownership request`() {
        val saved = stale.copy(priority = QueueOrigin.USER_REQUESTED_PRIORITY, requiresUpdateOwnership = true)
        val merged = QueueRequestRetention.merge(stale.copy(id = "replacement"), null, saved)
        assertEquals(QueueOrigin.USER_REQUESTED_PRIORITY, merged.priority)
        assertTrue(merged.requiresUpdateOwnership)
        assertEquals("replacement", merged.id)
    }

    @Test
    fun `a new explicit request is not lowered to the older automatic snapshot`() {
        val merged = QueueRequestRetention.merge(stale.copy(priority = QueueOrigin.USER_REQUESTED_PRIORITY), stale)
        assertEquals(QueueOrigin.USER_REQUESTED_PRIORITY, merged.priority)
        assertFalse(merged.requiresUpdateOwnership)
    }

    @Test
    fun `a user visible check stays distinct from permission to install`() {
        val merged = QueueRequestRetention.merge(stale, stale.copy(priority = QueueOrigin.USER_VISIBLE_PRIORITY))
        assertEquals(QueueOrigin.USER_VISIBLE_PRIORITY, merged.priority)
        assertFalse(QueueOrigin.isUserRequested(merged.priority))
    }
}
