package dev.wystore.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationLayoutPolicyTest {
    @Test fun `short leading labels share a row while long trailing labels get room`() {
        assertEquals(listOf(3, 2), NavigationLayoutPolicy.rows(listOf(70, 60, 75, 100, 100), 300))
    }
    @Test fun `all destinations remain in one row when every label fits`() {
        assertEquals(listOf(5), NavigationLayoutPolicy.rows(listOf(50, 40, 50, 60, 60), 320))
    }
    @Test fun `unequal labels share a row when their combined widths fit`() {
        assertEquals(listOf(5), NavigationLayoutPolicy.rows(listOf(35, 25, 35, 80, 60), 250))
    }
    @Test fun `no destination disappears in an extremely narrow window`() {
        assertEquals(listOf(1, 1), NavigationLayoutPolicy.rows(listOf(100, 100), 60))
    }
}
