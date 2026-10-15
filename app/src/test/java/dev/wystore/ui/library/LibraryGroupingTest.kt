package dev.wystore.ui.library

import dev.wystore.data.InstallSource
import dev.wystore.data.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryGroupingTest {

    private fun app(name: String) = InstalledApp(name, name, "1", 1, 0, InstallSource.OTHER, emptySet())
    private val apps = listOf(app("a"), app("b"), app("c"), app("d"))
    private val adopted = setOf("b", "d")

    private fun List<LibraryGrouping.Group>.shape() = map { it.key to it.apps.map { app -> app.packageName } }

    @Test
    fun `under All the apps not accepted yet are a group of their own, in the same order`() {
        assertEquals(
            listOf(LibraryGrouping.ADOPTED to listOf("b", "d"), LibraryGrouping.NOT_ADOPTED to listOf("a", "c")),
            LibraryGrouping.groups(apps, adopted, LibrarySort.ALL, selecting = false).shape()
        )
    }

    @Test
    fun `an empty group is not shown`() {
        assertEquals(
            listOf(LibraryGrouping.ADOPTED to listOf("b", "d")),
            LibraryGrouping.groups(apps.filter { it.packageName in adopted }, adopted, LibrarySort.ALL, false).shape()
        )
    }

    @Test
    fun `the Not accepted filter and choosing several show only what can be accepted`() {
        val expected = listOf(LibraryGrouping.NOT_ADOPTED to listOf("a", "c"))
        assertEquals(expected, LibraryGrouping.groups(apps, adopted, LibrarySort.NOT_ADOPTED, false).shape())
        assertEquals(expected, LibraryGrouping.groups(apps, adopted, LibrarySort.GITHUB, selecting = true).shape())
    }

    @Test
    fun `other filters stay one untitled list`() {
        val groups = LibraryGrouping.groups(apps, adopted, LibrarySort.AUTO_ON, false)
        assertEquals(1, groups.size)
        assertEquals(false, groups.single().titled)
    }
}
