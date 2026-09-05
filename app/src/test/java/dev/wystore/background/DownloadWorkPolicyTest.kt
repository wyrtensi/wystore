package dev.wystore.background

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadWorkPolicyTest {

    @Test
    fun workNameIsUniquePerQueueId() {
        val name1 = TransferDispatcher.downloadWorkName("queue-1")
        val name2 = TransferDispatcher.downloadWorkName("queue-2")

        assertEquals("wystore:download:queue-1", name1)
        assertEquals("wystore:download:queue-2", name2)
        assertNotEquals(name1, name2)
    }

    @Test
    fun selectsTransferMechanismBasedOnSdkLevel() {
        // API 26-33 selects WorkManager foreground
        assertEquals(
            TransferDispatcher.TransferMechanism.WORK_MANAGER_FOREGROUND,
            TransferDispatcher.determineMechanism(sdkInt = 26, uidtSchedulingAllowed = true)
        )
        assertEquals(
            TransferDispatcher.TransferMechanism.WORK_MANAGER_FOREGROUND,
            TransferDispatcher.determineMechanism(sdkInt = 33, uidtSchedulingAllowed = true)
        )

        // API 34+ selects User Initiated Data Transfer (UIDT)
        assertEquals(
            TransferDispatcher.TransferMechanism.USER_INITIATED_JOB,
            TransferDispatcher.determineMechanism(sdkInt = 34, uidtSchedulingAllowed = true)
        )
        assertEquals(
            TransferDispatcher.TransferMechanism.USER_INITIATED_JOB,
            TransferDispatcher.determineMechanism(sdkInt = 36, uidtSchedulingAllowed = true)
        )

        // Refused UIDT scheduling on API 34+ falls back to WorkManager foreground
        assertEquals(
            TransferDispatcher.TransferMechanism.WORK_MANAGER_FOREGROUND,
            TransferDispatcher.determineMechanism(sdkInt = 34, uidtSchedulingAllowed = false)
        )
    }
}
