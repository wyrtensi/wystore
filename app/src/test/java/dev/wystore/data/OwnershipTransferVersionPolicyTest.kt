package dev.wystore.data

import org.junit.Assert.assertEquals
import org.junit.Test

class OwnershipTransferVersionPolicyTest {
    @Test fun lowerKnownVersionCodeIsAnApkDowngrade() {
        assertEquals(TransferVersionWarning.APK_DOWNGRADE,
            OwnershipTransferVersionPolicy.warning("3.0", 300, "4.0", 200, false))
    }

    @Test fun knownVersionCodeTakesPriorityOverAnOlderTag() {
        assertEquals(TransferVersionWarning.NONE,
            OwnershipTransferVersionPolicy.warning("3.0", 300, "v2.0", 400, true))
    }

    @Test fun olderGitHubTagWarnsAboutReleaseWithoutClaimingApkIdentity() {
        assertEquals(TransferVersionWarning.OLDER_RELEASE,
            OwnershipTransferVersionPolicy.warning("3.0", 300, "v2.0", null, true))
    }

    @Test fun unparseableReleaseDoesNotInventADowngrade() {
        assertEquals(TransferVersionWarning.NONE,
            OwnershipTransferVersionPolicy.warning("3.0", 300, "nightly", null, true))
    }

    @Test fun missingTargetDoesNotInventADowngrade() {
        assertEquals(TransferVersionWarning.NONE,
            OwnershipTransferVersionPolicy.warning("3.0", 300, null, null, false))
    }

    @Test fun sameOrNewerCodeDoesNotWarn() {
        for (target in listOf(300L, 400L)) {
            assertEquals(TransferVersionWarning.NONE,
                OwnershipTransferVersionPolicy.warning("3.0", 300, "3.0", target, false))
        }
    }
}
