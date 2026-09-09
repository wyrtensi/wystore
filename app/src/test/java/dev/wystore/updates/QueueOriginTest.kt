package dev.wystore.updates

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueOriginTest {

    /** Rows a check created carry the background priority; nobody asked for those individually. */
    @Test
    fun aRowFoundByACheckIsNotAUserRequest() {
        assertFalse(QueueOrigin.isUserRequested(0))
    }

    /**
     * A tap on "hand updates to Wy Store" or "reinstall" enqueues at the user priority, and that is
     * what tells the verifier to let the version already installed through: installing it is the
     * point, because the install is what moves the installer of record.
     */
    @Test
    fun aRowSomebodyAskedForIsRecognised() {
        assertTrue(QueueOrigin.isUserRequested(QueueOrigin.USER_REQUESTED_PRIORITY))
        assertTrue(QueueOrigin.isUserRequested(QueueOrigin.USER_REQUESTED_PRIORITY + 5))
    }

    /**
     * Not fetching an excluded app when the check finds it only holds until the queue passes the
     * turn to itself: the row waits at AVAILABLE, and any other update finishing hands it the slot.
     */
    @Test
    fun anAppExcludedFromAutoUpdatesIsSteppedOverWhenTheQueueStartsItself() {
        assertFalse(
            QueueOrigin.mayStartUnattended(priority = 0, autoUpdateEnabledForApp = false)
        )
        assertTrue(
            QueueOrigin.mayStartUnattended(priority = 0, autoUpdateEnabledForApp = true)
        )
    }

    /** The switch is about what happens unasked, and pressing "install" is asking. */
    @Test
    fun aRowSomebodyAskedForStartsWhateverTheSwitchSays() {
        assertTrue(
            QueueOrigin.mayStartUnattended(
                priority = QueueOrigin.USER_REQUESTED_PRIORITY,
                autoUpdateEnabledForApp = false
            )
        )
    }
}
