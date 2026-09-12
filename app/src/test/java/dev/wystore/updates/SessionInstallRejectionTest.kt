package dev.wystore.updates

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Telling a firmware that refuses installer sessions from an install that simply failed.
 *
 * MIUI with its "optimisation" switched on rejects every session a third-party store commits, with
 * the same line each time; its own installer, reached through the install intent, takes the very
 * same APK. The line is the only thing that says which of the two happened.
 */
class SessionInstallRejectionTest {

    @Test
    fun `the line MIUI answers a session with is recognised`() {
        assertTrue(SessionInstallRejection.isFirmwareRefusal("INSTALL_FAILED_INTERNAL_ERROR: Permission Denied"))
    }

    @Test
    fun `case and surrounding text do not matter`() {
        assertTrue(SessionInstallRejection.isFirmwareRefusal("Failure [INSTALL_FAILED_INTERNAL_ERROR: permission denied]"))
    }

    @Test
    fun `other internal errors are real failures and are left alone`() {
        assertFalse(SessionInstallRejection.isFirmwareRefusal("INSTALL_FAILED_INTERNAL_ERROR: Session relinquished"))
        assertFalse(SessionInstallRejection.isFirmwareRefusal("INSTALL_FAILED_INSUFFICIENT_STORAGE"))
        assertFalse(SessionInstallRejection.isFirmwareRefusal("INSTALL_FAILED_UPDATE_INCOMPATIBLE: Permission Denied"))
    }

    @Test
    fun `no message is no evidence`() {
        assertFalse(SessionInstallRejection.isFirmwareRefusal(null))
        assertFalse(SessionInstallRejection.isFirmwareRefusal(""))
    }
}
