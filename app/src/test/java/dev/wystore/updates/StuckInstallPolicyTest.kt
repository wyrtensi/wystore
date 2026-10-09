package dev.wystore.updates

import dev.wystore.updates.StuckInstallPolicy.Outcome
import org.junit.Assert.assertEquals
import org.junit.Test

class StuckInstallPolicyTest {

    private val now = 10_000_000L
    private val longAgo = now - StuckInstallPolicy.GRACE_MILLIS - 1
    private val justNow = now - 1_000

    @Test
    fun leavesAnInstallNobodyWasAskedAbout() {
        // Root and background installs, and the legacy installer, never ask the user; their rows
        // are not this policy's to judge - a long root install is still running.
        assertEquals(Outcome.LEAVE, StuckInstallPolicy.decide(100, awaitingUserSince = null, now = now, installedVersionCode = null))
    }

    @Test
    fun leavesADialogThatWasJustShown() {
        // The result broadcast for an answer given a moment ago is still on its way.
        assertEquals(Outcome.LEAVE, StuckInstallPolicy.decide(100, justNow, now, installedVersionCode = null))
    }

    @Test
    fun abandonedDialogGoesBackToReady() {
        assertEquals(Outcome.RETURN_TO_READY, StuckInstallPolicy.decide(100, longAgo, now, installedVersionCode = null))
    }

    @Test
    fun olderVersionOnDeviceIsNotTheInstall() {
        assertEquals(Outcome.RETURN_TO_READY, StuckInstallPolicy.decide(100, longAgo, now, installedVersionCode = 99))
    }

    @Test
    fun installThatLandedWithoutAReportIsFinished() {
        assertEquals(Outcome.SUCCEEDED, StuckInstallPolicy.decide(100, longAgo, now, installedVersionCode = 100))
    }
}
