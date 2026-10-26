package dev.wystore.data

import dev.wystore.updates.QueueOrigin
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GooglePlayUpdatePolicyTest {
    private val managed = ManagedApp("test.app", "Test", setOf("certificate"))
    private val installed = InstalledApp("test.app", "Test", "2", 2L, 0L,
        InstallSource.WY_STORE, setOf("certificate"),
        installerPackageName = "com.android.vending", updateOwnerPackageName = "app.wystore")

    @Test fun `recorded Play installer pauses even when Wy Store owns updates`() {
        assertTrue(GooglePlayUpdatePolicy.isPaused(managed, installed, false))
    }

    @Test fun `Play attribution stays paused after installer attribution changes`() {
        assertTrue(GooglePlayUpdatePolicy.isPaused(managed.copy(googlePlayUpdateDetected = true),
            installed.copy(installerPackageName = "app.wystore"), false))
        assertTrue(GooglePlayUpdatePolicy.isPaused(managed.copy(googlePlayUpdateDetected = true), null, false))
    }

    @Test fun `global override allows checking without erasing attribution`() {
        val observed = managed.copy(googlePlayUpdateDetected = true)
        assertFalse(GooglePlayUpdatePolicy.isPaused(observed, installed, true))
        assertTrue(GooglePlayUpdatePolicy.hasGooglePlayUpdate(observed, null))
        assertTrue(GooglePlayUpdatePolicy.isPaused(observed, installed, false))
    }

    @Test fun `per app consent allows checking without enabling excluded auto updates`() {
        val excluded = managed.copy(forceWyStore = true, autoUpdate = false, googlePlayUpdateDetected = true)
        assertFalse(GooglePlayUpdatePolicy.isPaused(excluded, installed, false))
        assertFalse(QueueOrigin.mayStartUnattended(0, excluded.autoUpdate))
    }

    @Test fun `manual check priority does not authorize unattended transfers`() {
        assertFalse(GooglePlayUpdatePolicy.mayProceed(managed, installed, false, QueueOrigin.USER_VISIBLE_PRIORITY))
        assertTrue(GooglePlayUpdatePolicy.mayProceed(managed, installed, false, QueueOrigin.USER_REQUESTED_PRIORITY))
    }

    @Test fun `update owner alone is not evidence of a Play update`() {
        assertFalse(GooglePlayUpdatePolicy.isPaused(managed,
            installed.copy(installerPackageName = "app.wystore", updateOwnerPackageName = "com.android.vending"), false))
    }

    @Test fun `an unobserved app remains eligible`() {
        assertFalse(GooglePlayUpdatePolicy.isPaused(managed, null, false))
    }

    @Test fun `a removed app cannot proceed with an unattended install`() {
        assertFalse(GooglePlayUpdatePolicy.mayProceed(managed, null, false, 0))
        assertTrue(GooglePlayUpdatePolicy.mayProceed(managed, null, false, QueueOrigin.USER_REQUESTED_PRIORITY))
    }
}
