package dev.wystore.updates

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallCallbackTokenTest {
    @Test
    fun acceptsOnlyExactNonEmptyToken() {
        assertTrue(InstallCallbackToken.matches("expected-token", "expected-token"))
        assertFalse(InstallCallbackToken.matches("expected-token", "different-token"))
        assertFalse(InstallCallbackToken.matches("expected-token", null))
        assertFalse(InstallCallbackToken.matches(null, "expected-token"))
        assertFalse(InstallCallbackToken.matches("", ""))
    }
}
