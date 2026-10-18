package dev.wystore.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/** Rows drawn as one panel: which corners each rounds, and where the hairlines go. */
class GroupPositionTest {

    @Test
    fun aLoneRowIsAPanelOfItsOwn() {
        assertEquals(GroupPosition.SINGLE, groupPosition(0, 1))
    }

    @Test
    fun aGroupOpensAndClosesOnItsEnds() {
        assertEquals(
            listOf(GroupPosition.FIRST, GroupPosition.MIDDLE, GroupPosition.MIDDLE, GroupPosition.LAST),
            (0 until 4).map { groupPosition(it, 4) }
        )
        assertEquals(listOf(GroupPosition.FIRST, GroupPosition.LAST), (0 until 2).map { groupPosition(it, 2) })
    }
}
