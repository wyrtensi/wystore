package dev.wystore.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SigningFlagsTest {
    @Test
    fun androidEightRequestsLegacySignatures() {
        assertEquals(0x00000040, SigningFlags.forSdk(26))
    }

    @Test
    fun androidNineAndNewerRequestSigningCertificates() {
        assertEquals(0x08000000, SigningFlags.forSdk(28))
        assertEquals(0x08000000, SigningFlags.forSdk(36))
    }
}
