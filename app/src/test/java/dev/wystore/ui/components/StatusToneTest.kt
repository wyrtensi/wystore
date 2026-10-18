package dev.wystore.ui.components

import dev.wystore.InstallQueueStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class StatusToneTest {

    @Test
    fun everyAppStateHasATone() {
        // Exhaustive by construction; this pins the ones a user tells apart on sight.
        assertEquals(StatusTone.READY, StatusTones.of(StatusCode.READY_TO_INSTALL))
        assertEquals(StatusTone.ACTIVE, StatusTones.of(StatusCode.DOWNLOADING))
        assertEquals(StatusTone.PAUSED, StatusTones.of(StatusCode.PAUSED))
        assertEquals(StatusTone.ASKING, StatusTones.of(StatusCode.AWAITING_USER_CONFIRMATION))
        assertEquals(StatusTone.FAILED, StatusTones.of(StatusCode.FAILED_NETWORK))
        assertEquals(StatusTone.NEUTRAL, StatusTones.of(StatusCode.QUEUED))
    }

    @Test
    fun aSourceThatWouldNotVouchForItsFileIsAQuestionNotAFailure() {
        assertEquals(StatusTone.ASKING, StatusTones.of(StatusCode.FAILED_SOURCE_UNCONFIRMED))
    }

    @Test
    fun anInstallWaitingOnAndroidsDialogIsAQuestion() {
        assertEquals(StatusTone.ASKING, StatusTones.of(InstallQueueStatus.INSTALLING, awaitingConfirmation = true))
        assertEquals(StatusTone.ACTIVE, StatusTones.of(InstallQueueStatus.INSTALLING, awaitingConfirmation = false))
        assertEquals(StatusTone.READY, StatusTones.of(InstallQueueStatus.READY, awaitingConfirmation = false))
    }
}
