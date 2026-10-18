package dev.wystore.updates

import android.content.pm.PackageInstaller
import dev.wystore.updates.model.QueueErrorCode
import org.junit.Assert.assertEquals
import org.junit.Test

class InstallRefusalTest {

    @Test
    fun aCopySignedWithAnotherKeyIsAConflictNotAFailure() {
        // What Android wrote for Messenger Team installed into Shelter beside its main-profile copy.
        val message = "INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package center.basis.MessengerTeam " +
            "signatures do not match newer version; ignoring!"

        assertEquals(
            QueueErrorCode.INSTALL_CONFLICT,
            InstallRefusal.codeFor(PackageInstaller.STATUS_FAILURE_CONFLICT, message)
        )
    }

    @Test
    fun theStatusAloneIsEnoughWhenTheMessageNamesNothing() {
        assertEquals(QueueErrorCode.INSTALL_CONFLICT, InstallRefusal.codeFor(PackageInstaller.STATUS_FAILURE_CONFLICT, null))
        assertEquals(QueueErrorCode.STORAGE_FULL, InstallRefusal.codeFor(PackageInstaller.STATUS_FAILURE_STORAGE, ""))
        assertEquals(QueueErrorCode.INCOMPATIBLE, InstallRefusal.codeFor(PackageInstaller.STATUS_FAILURE_INCOMPATIBLE, null))
    }

    @Test
    fun theNamedReasonWinsOverACoarseStatus() {
        assertEquals(
            QueueErrorCode.INCOMPATIBLE,
            InstallRefusal.codeFor(PackageInstaller.STATUS_FAILURE, "INSTALL_FAILED_NO_MATCHING_ABIS: Failed to extract native libraries")
        )
        assertEquals(
            QueueErrorCode.DOWNGRADE,
            InstallRefusal.codeFor(PackageInstaller.STATUS_FAILURE_INCOMPATIBLE, "INSTALL_FAILED_VERSION_DOWNGRADE: Downgrade detected")
        )
    }

    @Test
    fun theSystemInstallersNumberedCodeIsReadByItsName() {
        // The route without sessions only has the number, described by LegacyInstallOutcome.
        assertEquals(
            QueueErrorCode.INSTALL_CONFLICT,
            InstallRefusal.codeFor(null, LegacyInstallOutcome.describe(-7))
        )
        assertEquals(
            QueueErrorCode.STORAGE_FULL,
            InstallRefusal.codeFor(null, LegacyInstallOutcome.describe(-4))
        )
    }

    @Test
    fun anythingElseStaysAPlainFailure() {
        assertEquals(QueueErrorCode.INSTALL_FAILED, InstallRefusal.codeFor(PackageInstaller.STATUS_FAILURE, "something else"))
        assertEquals(QueueErrorCode.INSTALL_FAILED, InstallRefusal.codeFor(null, null))
        assertEquals(QueueErrorCode.INSTALL_FAILED, InstallRefusal.codeFor(PackageInstaller.STATUS_FAILURE_INVALID, null))
    }
}
