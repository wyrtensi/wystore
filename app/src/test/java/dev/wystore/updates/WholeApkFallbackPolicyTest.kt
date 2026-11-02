package dev.wystore.updates

import dev.wystore.data.ManagedSource
import org.junit.Assert.*
import org.junit.Test

class WholeApkFallbackPolicyTest {
    @Test fun `library delivery never replaces its selected complete set with a whole APK request`() {
        assertFalse(WholeApkFallbackPolicy.mayRefetch(ManagedSource.WY_STORE))
        assertTrue(WholeApkFallbackPolicy.mayRefetch(ManagedSource.RUSTORE))
        assertTrue(WholeApkFallbackPolicy.mayRefetch(ManagedSource.GITHUB))
    }
}
