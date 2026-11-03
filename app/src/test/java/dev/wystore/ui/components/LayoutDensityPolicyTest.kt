package dev.wystore.ui.components

import dev.wystore.settings.LayoutDensity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutDensityPolicyTest {
    @Test fun explicitChoicesOverrideAutomaticWidth() {
        assertTrue(LayoutDensityPolicy.isCompact(LayoutDensity.COMPACT, 800f, 1f, false))
        assertFalse(LayoutDensityPolicy.isCompact(LayoutDensity.STANDARD, 320f, 1.3f, false))
        assertFalse(LayoutDensityPolicy.isCompact(LayoutDensity.COMPACT, 320f, 1f, true))
    }
    @Test fun automaticUsesSystemWidthAndFont() {
        assertTrue(LayoutDensityPolicy.isCompact(LayoutDensity.AUTO, 400f, 1f, false))
        assertTrue(LayoutDensityPolicy.isCompact(LayoutDensity.AUTO, 480f, 1.2f, false))
        assertFalse(LayoutDensityPolicy.isCompact(LayoutDensity.AUTO, 600f, 1f, false))
    }
}
