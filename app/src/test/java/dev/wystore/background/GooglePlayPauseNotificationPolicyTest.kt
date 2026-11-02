package dev.wystore.background

import org.junit.Assert.assertEquals
import org.junit.Test

class GooglePlayPauseNotificationPolicyTest {
    @Test fun `dismissed and fluctuating known incidents do not reappear`() {
        val notified = setOf("a", "b")
        for (current in listOf(setOf("a"), setOf("b"), setOf("a", "b"))) {
            assertEquals(PauseNotificationAction.KEEP, GooglePlayPauseNotificationPolicy.decide(true, current, notified))
        }
    }
    @Test fun `only new incidents publish and an empty or disabled category cancels`() {
        assertEquals(PauseNotificationAction.PUBLISH, GooglePlayPauseNotificationPolicy.decide(true, setOf("a", "b"), setOf("a")))
        assertEquals(PauseNotificationAction.CANCEL, GooglePlayPauseNotificationPolicy.decide(true, emptySet(), setOf("a")))
        assertEquals(PauseNotificationAction.CANCEL, GooglePlayPauseNotificationPolicy.decide(false, setOf("a"), emptySet()))
    }
}
