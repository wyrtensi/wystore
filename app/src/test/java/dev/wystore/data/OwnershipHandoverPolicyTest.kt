package dev.wystore.data

import org.junit.Assert.assertEquals
import org.junit.Test

class OwnershipHandoverPolicyTest {
    @Test fun `an installer record alone still requires a fresh installation`() {
        assertEquals(OwnershipHandover.REINSTALL, decide(owner = null))
    }

    @Test fun `another update owner requires a fresh installation`() {
        assertEquals(OwnershipHandover.REINSTALL, decide(owner = "another.store"))
    }

    @Test fun `only the actual update owner needs no handover`() {
        assertEquals(OwnershipHandover.OWNED, decide(owner = "app.wystore"))
    }

    @Test fun `old Android explains the limitation before an uninstall`() {
        assertEquals(OwnershipHandover.UNSUPPORTED_ANDROID, decide(sdk = 33))
    }

    @Test fun `firmware without installer sessions must not offer destructive handover`() {
        assertEquals(OwnershipHandover.UNSUPPORTED_INSTALLER, decide(sessions = false))
    }

    private fun decide(owner: String? = null, sdk: Int = 34, sessions: Boolean = true) =
        OwnershipHandoverPolicy.decide(sdk, owner, "app.wystore", sessions)
}
