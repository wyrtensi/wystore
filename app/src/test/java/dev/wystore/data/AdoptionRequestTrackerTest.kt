package dev.wystore.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdoptionRequestTrackerTest {
    @Test fun `a skipped lookup must not authorize removing its app later`() {
        val requests = AdoptionRequestTracker()
        val skipped = requests.begin()
        requests.invalidate()
        assertFalse(requests.isCurrent(skipped))
    }

    @Test fun `another prompt invalidates an earlier lookup even for the same app`() {
        val requests = AdoptionRequestTracker()
        val earlier = requests.begin()
        val current = requests.begin()
        assertFalse(requests.isCurrent(earlier))
        assertTrue(requests.isCurrent(current))
    }
}
