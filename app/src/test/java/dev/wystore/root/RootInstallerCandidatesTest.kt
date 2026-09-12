package dev.wystore.root

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The list of places an su binary is looked for.
 *
 * It used to be one hardcoded absolute path, so a device keeping su anywhere else was reported as
 * having no root at all - which is a different thing to tell someone than "it failed", and the
 * wrong one. An emulator image is the easy counter-example: its su lives in /system/xbin.
 */
class RootInstallerCandidatesTest {

    @Test
    fun `the name on PATH is tried before any absolute path`() {
        assertEquals("su", RootInstaller.CANDIDATES.first())
    }

    @Test
    fun `the places root managers actually use are all covered`() {
        assertTrue(RootInstaller.CANDIDATES.contains("/system/bin/su"))
        assertTrue(RootInstaller.CANDIDATES.contains("/system/xbin/su"))
        assertTrue(RootInstaller.CANDIDATES.contains("/sbin/su"))
        assertTrue(RootInstaller.CANDIDATES.contains("/su/bin/su"))
        assertTrue(RootInstaller.CANDIDATES.contains("/debug_ramdisk/su"))
    }

    @Test
    fun `no place is searched twice`() {
        assertEquals(RootInstaller.CANDIDATES.size, RootInstaller.CANDIDATES.distinct().size)
    }
}
