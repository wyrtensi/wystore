package dev.wystore.updates

import android.content.pm.PackageInstaller
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SilentInstallRefusalTest {

    private val prepared = InstallSessionWriter.SESSION_PREPARED
    private val committed = InstallSessionWriter.SESSION_COMMITTED
    private val askedUser = InstallSessionWriter.SESSION_AWAITING_USER

    @Test
    fun anAbortedSilentSessionNobodyWasAskedAboutIsTheSystemsRefusal() {
        // What HyperOS on Android 16 answers: no dialog, the session aborted at once.
        // commitSession marks the session COMMITTED right before PackageInstaller.Session.commit,
        // so live refusals arrive with that state rather than PREPARED.
        assertTrue(SilentInstallRefusal.isRefusal(PackageInstaller.STATUS_FAILURE_ABORTED, silentRequested = true, sessionState = prepared))
        assertTrue(SilentInstallRefusal.isRefusal(PackageInstaller.STATUS_FAILURE_ABORTED, silentRequested = true, sessionState = committed))
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
        assertFalse(SilentInstallRefusal.isRefusal(PackageInstaller.STATUS_FAILURE_ABORTED, silentRequested = false, sessionState = committed))
    }

    @Test
    fun otherOutcomesAreNotRefusals() {
        assertFalse(SilentInstallRefusal.isRefusal(PackageInstaller.STATUS_SUCCESS, silentRequested = true, sessionState = prepared))
        assertFalse(SilentInstallRefusal.isRefusal(PackageInstaller.STATUS_SUCCESS, silentRequested = true, sessionState = committed))
        assertFalse(SilentInstallRefusal.isRefusal(PackageInstaller.STATUS_FAILURE, silentRequested = true, sessionState = prepared))
        assertFalse(SilentInstallRefusal.isRefusal(PackageInstaller.STATUS_FAILURE, silentRequested = true, sessionState = committed))
    }

    @Test
    fun aSessionWithNoRecordIsNotJudged() {
        assertFalse(SilentInstallRefusal.isRefusal(PackageInstaller.STATUS_FAILURE_ABORTED, silentRequested = true, sessionState = null))
    }
}
