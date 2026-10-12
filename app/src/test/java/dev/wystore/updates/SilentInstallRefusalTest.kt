package dev.wystore.updates

import android.content.pm.PackageInstaller
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SilentInstallRefusalTest {

    private val prepared = InstallSessionWriter.SESSION_PREPARED
    private val askedUser = InstallSessionWriter.SESSION_AWAITING_USER

    @Test
    fun anAbortedSilentSessionNobodyWasAskedAboutIsTheSystemsRefusal() {
        // What HyperOS on Android 16 answers: no dialog, the session aborted at once.
        assertTrue(SilentInstallRefusal.isRefusal(PackageInstaller.STATUS_FAILURE_ABORTED, silentRequested = true, sessionState = prepared))
    }

    @Test
    fun theUserCancellingTheDialogIsTheirAnswer() {
        // Android declined the silent request the documented way and showed the dialog; a cancel
        // there must not bring the dialog straight back.
        assertFalse(SilentInstallRefusal.isRefusal(PackageInstaller.STATUS_FAILURE_ABORTED, silentRequested = true, sessionState = askedUser))
    }

    @Test
    fun anOrdinarySessionIsNeverARefusal() {
        assertFalse(SilentInstallRefusal.isRefusal(PackageInstaller.STATUS_FAILURE_ABORTED, silentRequested = false, sessionState = prepared))
    }

    @Test
    fun otherOutcomesAreNotRefusals() {
        assertFalse(SilentInstallRefusal.isRefusal(PackageInstaller.STATUS_SUCCESS, silentRequested = true, sessionState = prepared))
        assertFalse(SilentInstallRefusal.isRefusal(PackageInstaller.STATUS_FAILURE, silentRequested = true, sessionState = prepared))
    }

    @Test
    fun aSessionWithNoRecordIsNotJudged() {
        assertFalse(SilentInstallRefusal.isRefusal(PackageInstaller.STATUS_FAILURE_ABORTED, silentRequested = true, sessionState = null))
    }
}
