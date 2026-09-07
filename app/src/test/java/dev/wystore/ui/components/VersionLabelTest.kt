package dev.wystore.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VersionLabelTest {

    @Test
    fun `drops the build metadata a publisher appends`() {
        assertEquals("2026.08.4", shortVersionName("2026.08.4 #162.1gpr"))
        assertEquals("5.0", shortVersionName("5.0 build 12"))
        assertEquals("1.2.3", shortVersionName("1.2.3#4567"))
    }

    @Test
    fun `leaves an ordinary version alone`() {
        assertEquals("8.194", shortVersionName("8.194"))
        assertEquals("v1.0.0-beta.2", shortVersionName("v1.0.0-beta.2"))
        assertEquals("1.423.966222932", shortVersionName("1.423.966222932"))
    }

    @Test
    fun `has nothing to say about a missing version`() {
        assertNull(shortVersionName(null))
        assertNull(shortVersionName("   "))
    }

    @Test
    fun `keeps something to show when the tail is all there is`() {
        assertEquals("#42", shortVersionName("#42"))
    }
}
