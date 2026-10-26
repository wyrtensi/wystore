package dev.wystore.updates

import dev.wystore.data.ManagedSource
import dev.wystore.updates.model.QueueErrorCode
import dev.wystore.updates.model.QueueItemSnapshot
import dev.wystore.updates.model.QueueState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OwnershipTransferResultTest {
    @Test
    fun ordinaryInstallDoesNotRequireUpdateOwnership() {
        val result = succeed(item(requiresOwnership = false), sdk = 34, owner = "another.store")

        assertEquals(QueueState.INSTALLED, result.state)
        assertNull(result.errorCode)
    }

    @Test
    fun handoverIsConfirmedOnlyForWyStoreAsTheActualOwner() {
        val result = succeed(item(), sdk = 34, owner = "app.wystore")

        assertEquals(QueueState.INSTALLED, result.state)
        assertNull(result.errorCode)
    }

    @Test
    fun absentOwnerLeavesTheInstallationSuccessfulAndHandoverUnconfirmed() {
        val result = succeed(item(), sdk = 34, owner = null)

        assertEquals(QueueState.INSTALLED, result.state)
        assertEquals(QueueErrorCode.UPDATE_OWNERSHIP_UNCONFIRMED, result.errorCode)
    }

    @Test
    fun anotherStoreAsOwnerDoesNotConfirmTheHandover() {
        val result = succeed(item(), sdk = 34, owner = "ru.vk.store")

        assertEquals(QueueState.INSTALLED, result.state)
        assertEquals(QueueErrorCode.UPDATE_OWNERSHIP_UNCONFIRMED, result.errorCode)
    }

    @Test
    fun androidWithoutOwnershipSupportCannotConfirmTheHandover() {
        val result = succeed(item(), sdk = 33, owner = "app.wystore")

        assertEquals(QueueState.INSTALLED, result.state)
        assertEquals(QueueErrorCode.UPDATE_OWNERSHIP_UNCONFIRMED, result.errorCode)
    }

    @Test
    fun duplicateSuccessDoesNotEraseAnUnconfirmedHandover() {
        val first = succeed(item(), sdk = 34, owner = null)
        val duplicate = succeed(first, sdk = 34, owner = "app.wystore")

        assertEquals(QueueState.INSTALLED, duplicate.state)
        assertEquals(QueueErrorCode.UPDATE_OWNERSHIP_UNCONFIRMED, duplicate.errorCode)
    }

    private fun succeed(item: QueueItemSnapshot, sdk: Int, owner: String?) =
        OwnershipTransferResult.successfulInstall(item, sdk, "app.wystore", owner)

    private fun item(requiresOwnership: Boolean = true) = QueueItemSnapshot(
        id = "handover",
        packageName = "example.app",
        label = "Example",
        versionName = "1.0",
        versionCode = 1,
        source = ManagedSource.RUSTORE,
        state = QueueState.INSTALLING,
        priority = 10,
        position = 0,
        requiresUpdateOwnership = requiresOwnership
    )
}
