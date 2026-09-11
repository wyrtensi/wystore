package dev.wystore.permissions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPermissionPolicyTest {

    private val tiramisu = 33
    private val beforeTiramisu = 32

    @Test
    fun `there is nothing to ask for once notifications are allowed`() {
        assertEquals(
            NotificationPermissionAction.NONE,
            NotificationPermissionPolicy.actionForRequest(
                sdkInt = tiramisu,
                granted = true,
                alreadyAsked = false
            )
        )
    }

    @Test
    fun `a first request on Android 13 is the system dialog, not a trip to Settings`() {
        assertEquals(
            NotificationPermissionAction.ASK_SYSTEM,
            NotificationPermissionPolicy.actionForRequest(
                sdkInt = tiramisu,
                granted = false,
                alreadyAsked = false
            )
        )
    }

    /** The dialog never appears twice, so offering it again would be a button that does nothing. */
    @Test
    fun `once the dialog has been spent only Settings is left`() {
        assertEquals(
            NotificationPermissionAction.OPEN_SETTINGS,
            NotificationPermissionPolicy.actionForRequest(
                sdkInt = tiramisu,
                granted = false,
                alreadyAsked = true
            )
        )
    }

    @Test
    fun `before Android 13 there is no permission to ask for`() {
        assertEquals(
            NotificationPermissionAction.OPEN_SETTINGS,
            NotificationPermissionPolicy.actionForRequest(
                sdkInt = beforeTiramisu,
                granted = false,
                alreadyAsked = false
            )
        )
    }

    /**
     * The whole point of spending the one dialog carefully: not at first launch, when the store is
     * looking after nothing and the question is about a future the user has not chosen.
     */
    @Test
    fun `an empty store does not ask on its own`() {
        assertFalse(
            NotificationPermissionPolicy.shouldOfferUnprompted(
                sdkInt = tiramisu,
                granted = false,
                alreadyAsked = false,
                hasSomethingToReportAbout = false
            )
        )
    }

    @Test
    fun `a store with something to look after asks once`() {
        assertTrue(
            NotificationPermissionPolicy.shouldOfferUnprompted(
                sdkInt = tiramisu,
                granted = false,
                alreadyAsked = false,
                hasSomethingToReportAbout = true
            )
        )
        assertFalse(
            NotificationPermissionPolicy.shouldOfferUnprompted(
                sdkInt = tiramisu,
                granted = false,
                alreadyAsked = true,
                hasSomethingToReportAbout = true
            )
        )
    }

    @Test
    fun `an older Android is never asked unprompted`() {
        assertFalse(
            NotificationPermissionPolicy.shouldOfferUnprompted(
                sdkInt = beforeTiramisu,
                granted = false,
                alreadyAsked = false,
                hasSomethingToReportAbout = true
            )
        )
    }
}
