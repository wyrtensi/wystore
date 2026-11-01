package dev.wystore.updates

import dev.wystore.updates.model.QueueState
import org.junit.Assert.assertEquals
import org.junit.Test

class PreparedInstallDispatcherTest {
    @Test fun `legacy replacement still offers confirmation and reuses an active transfer`() {
        assertEquals(PreparedInstallAction.OFFER_CONFIRMATION,
            PreparedInstallDispatcher.action(QueueState.READY_TO_INSTALL, false, true))
        assertEquals(PreparedInstallAction.KEEP_ACTIVE,
            PreparedInstallDispatcher.action(QueueState.DOWNLOADING, false, true))
        assertEquals(PreparedInstallAction.DOWNLOAD,
            PreparedInstallDispatcher.action(QueueState.AVAILABLE, false, true))
    }
    @Test fun `a verified ownership replacement offers confirmation without another network transfer`() {
        assertEquals(PreparedInstallAction.OFFER_CONFIRMATION,
            PreparedInstallDispatcher.action(QueueState.READY_TO_INSTALL, requiresUpdateOwnership = true))
    }

    @Test fun `an adopted running download keeps its existing runner`() {
        for (state in listOf(QueueState.CHECKING, QueueState.DOWNLOADING, QueueState.VERIFYING)) {
            assertEquals(PreparedInstallAction.KEEP_ACTIVE,
                PreparedInstallDispatcher.action(state, requiresUpdateOwnership = true))
        }
    }

    @Test fun `an ownership replacement without a downloaded APK starts its transfer`() {
        assertEquals(PreparedInstallAction.DOWNLOAD,
            PreparedInstallDispatcher.action(QueueState.AVAILABLE, requiresUpdateOwnership = true))
    }

    @Test fun `ordinary manual downloads retain their existing dispatch behavior`() {
        assertEquals(PreparedInstallAction.DOWNLOAD,
            PreparedInstallDispatcher.action(QueueState.AVAILABLE, requiresUpdateOwnership = false))
    }
}
