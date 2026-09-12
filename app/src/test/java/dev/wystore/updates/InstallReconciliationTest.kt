package dev.wystore.updates

import dev.wystore.data.ManagedSource
import dev.wystore.updates.model.QueueAction
import dev.wystore.updates.model.QueueErrorCode
import dev.wystore.updates.model.QueueItemSnapshot
import dev.wystore.updates.model.QueueState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallReconciliationTest {

    private fun installingSnapshot(
        packageName: String = "test.app",
        versionCode: Long = 100L
    ) = QueueItemSnapshot(
        id = "queue-installing-1",
        packageName = packageName,
        label = "Test App",
        versionName = "2.0.0",
        versionCode = versionCode,
        source = ManagedSource.RUSTORE,
        state = QueueState.INSTALLING,
        priority = 0,
        position = 0
    )

    @Test
    fun reconcilesSuccessCallback() {
        val action = InstallReconciliationPolicy.reconcileCallback(
            InstallReconciliationPolicy.CallbackResult.SUCCESS
        )
        assertEquals(QueueAction.InstallSucceeded, action)
    }

    @Test
    fun reconcilesCanceledInstaller() {
        val action = InstallReconciliationPolicy.reconcileCallback(
            InstallReconciliationPolicy.CallbackResult.CANCELLED
        )
        assertEquals(QueueAction.Cancel, action)
    }

    @Test
    fun reconcilesMissingCallbackFollowedByObservedPackageReplacement() {
        val item = installingSnapshot(versionCode = 100L)
        // System broadcast notifies package was replaced with versionCode 100
        val action = InstallReconciliationPolicy.reconcileObservedPackageChange(
            item = item,
            newVersionCode = 100L
        )
        assertEquals(QueueAction.InstallSucceeded, action)

        // Lower versionCode does not succeed
        val staleAction = InstallReconciliationPolicy.reconcileObservedPackageChange(
            item = item,
            newVersionCode = 99L
        )
        assertNull(staleAction)
    }

    @Test
    fun reconcilesStaleInstallingTimeout() {
        val item = installingSnapshot(versionCode = 100L)
        val now = 1_000_000L
        val staleTime = now - (InstallReconciliationPolicy.STALE_INSTALLING_TIMEOUT_MILLIS + 1000L)
        val recentTime = now - 10_000L

        assertTrue(InstallReconciliationPolicy.isStaleInstalling(item, staleTime, now))
        assertFalse(InstallReconciliationPolicy.isStaleInstalling(item, recentTime, now))

        // If stale and package is indeed updated -> success
        val successAction = InstallReconciliationPolicy.reconcileStaleInstalling(
            item = item,
            installedVersionCode = 100L
        )
        assertEquals(QueueAction.InstallSucceeded, successAction)

        // If stale and package not updated -> timeout failure
        val failureAction = InstallReconciliationPolicy.reconcileStaleInstalling(
            item = item,
            installedVersionCode = 50L
        )
        assertTrue(failureAction is QueueAction.InstallFailed)
        assertEquals(QueueErrorCode.TIMEOUT, (failureAction as QueueAction.InstallFailed).errorCode)
    }

    @Test
    fun verifiesSignatureCompatibility() {
        val valid = InstallReconciliationPolicy.checkSignatureCompatibility(
            installedDigests = setOf("hashA", "hashB"),
            updateDigests = setOf("hashA", "hashB")
        )
        assertTrue(valid)

        val mismatch = InstallReconciliationPolicy.checkSignatureCompatibility(
            installedDigests = setOf("hashA"),
            updateDigests = setOf("hashDifferent")
        )
        assertFalse(mismatch)
    }

    @Test
    fun verifiesAndroid8SingleApkRouting() {
        assertEquals(
            UserInstallRoute.LEGACY_SINGLE_APK,
            UserInstallRouting.select(sdkInt = 26, artifactCount = 1)
        )
        assertEquals(
            UserInstallRoute.LEGACY_SINGLE_APK,
            UserInstallRouting.select(sdkInt = 27, artifactCount = 1)
        )
        assertEquals(
            UserInstallRoute.PACKAGE_INSTALLER_SESSION,
            UserInstallRouting.select(sdkInt = 26, artifactCount = 2)
        )
        assertEquals(
            UserInstallRoute.PACKAGE_INSTALLER_SESSION,
            UserInstallRouting.select(sdkInt = 28, artifactCount = 1)
        )
        assertEquals(
            UserInstallRoute.PACKAGE_INSTALLER_SESSION,
            UserInstallRouting.select(sdkInt = 36, artifactCount = 1)
        )
        assertEquals(
            UserInstallRoute.LEGACY_SINGLE_APK,
            UserInstallRouting.select(sdkInt = 29, artifactCount = 1, sessionsRefused = true)
        )
    }
}
