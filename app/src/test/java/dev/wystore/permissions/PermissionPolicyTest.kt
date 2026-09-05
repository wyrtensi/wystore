package dev.wystore.permissions

import org.junit.Assert.assertNotNull
import org.junit.Test

class PermissionPolicyTest {

    @Test
    fun permissionSnapshotInstantiates() {
        val snapshot = PermissionSnapshot(
            notificationsGranted = true,
            canInstallUnknownApps = false,
            batteryOptimizationsIgnored = false
        )
        assertNotNull(snapshot)
    }
}
