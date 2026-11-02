package dev.wystore.data

import org.junit.Assert.*
import org.junit.Test

class SourceReplacementConsentTest {
    @Test fun `dismissal another choice or a package update invalidates removal consent`() {
        val installed = InstalledApp("org.example.app", "Example", "1", 1, 10, InstallSource.WY_STORE, setOf("key"))
        val consent = SourceReplacementConsent(7, 3, installed)
        assertTrue(consent.isCurrent(7, 3, installed))
        assertFalse(consent.isCurrent(null, 3, installed))
        assertFalse(consent.isCurrent(8, 3, installed))
        assertFalse(consent.isCurrent(7, 4, installed))
        assertFalse(consent.isCurrent(7, 3, null))
        assertFalse(consent.isCurrent(7, 3, installed.copy(versionCode = 2)))
        assertFalse(consent.isCurrent(7, 3, installed.copy(signingDigests = setOf("other"))))
        assertTrue(consent.isCurrent(7, 3, installed.copy(label = "New label")))
    }
}
