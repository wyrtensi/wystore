package dev.wystore.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SigningFlagsTest {

    private val getSignatures = 0x00000040
    private val getSigningCertificates = 0x08000000

    /** An installed package always has the modern field, on every version this app runs on. */
    @Test
    fun anInstalledPackageIsReadThroughSigningCertificates() {
        assertEquals(getSigningCertificates, SigningFlags.forSdk(28))
        assertEquals(getSigningCertificates, SigningFlags.forSdk(36))
    }

    /**
     * An APK on disk does not. Android 10 returns an archive with an empty `signingInfo`, and
     * asking only for the modern field made every finished download unverifiable there, so both
     * are asked for and whichever is filled in gets used.
     */
    @Test
    fun anArchiveIsReadThroughBothFields() {
        assertEquals(getSigningCertificates or getSignatures, SigningFlags.forArchive())
    }
}
