package dev.wystore.root

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertTrue(RootInstaller.CANDIDATES.contains("/vendor/bin/su"))
        assertTrue(RootInstaller.CANDIDATES.contains("/product/bin/su"))
        assertTrue(RootInstaller.CANDIDATES.contains("/system_ext/bin/su"))
        assertTrue(RootInstaller.CANDIDATES.contains("/data/adb/magisk/su"))
        assertTrue(RootInstaller.CANDIDATES.contains("/data/adb/ksu/bin/su"))
        assertTrue(RootInstaller.CANDIDATES.contains("/data/adb/ap/bin/su"))
    }

    @Test
    fun `probing skips binaries that spawn but do not grant uid 0`() {
        val tried = mutableListOf<String>()
        val result = RootShellPolicy.probeCandidates(
            cached = null,
            candidates = listOf("su", "/system/bin/su", "/debug_ramdisk/su", "/vendor/bin/su"),
            runId = { path ->
                tried += path
                when (path) {
                    "su" -> RootResult(false, "Permission denied")
                    "/system/bin/su" -> RootResult(true, "uid=10234(u0_a234)")
                    "/debug_ramdisk/su" -> null
                    "/vendor/bin/su" -> RootResult(true, "uid=0(root) gid=0(root)")
                    else -> null
                }
            }
        )
        assertEquals("/vendor/bin/su", result?.first)
        assertEquals(listOf("su", "/system/bin/su", "/debug_ramdisk/su", "/vendor/bin/su"), tried)
    }

    @Test
    fun `probing tries cached candidate first and falls through if it stops granting root`() {
        val tried = mutableListOf<String>()
        val result = RootShellPolicy.probeCandidates(
            cached = "/system/bin/su",
            candidates = listOf("su", "/system/bin/su", "/debug_ramdisk/su"),
            runId = { path ->
                tried += path
                if (path == "/debug_ramdisk/su") RootResult(true, "uid=0(root)") else RootResult(false, "denied")
            }
        )
        assertEquals("/debug_ramdisk/su", result?.first)
        assertEquals(listOf("/system/bin/su", "su", "/debug_ramdisk/su"), tried)
    }

    @Test
    fun `root is granted only when id reports uid 0`() {
        assertTrue(RootShellPolicy.grantsRoot("uid=0(root) gid=0(root) groups=0(root) context=u:r:magisk:s0"))
        assertFalse(RootShellPolicy.grantsRoot("uid=10234(u0_a234) gid=10234(u0_a234)"))
        assertFalse(RootShellPolicy.grantsRoot("Permission denied"))
        assertFalse(RootShellPolicy.grantsRoot(""))
    }

    @Test
    fun `no place is searched twice`() {
        assertEquals(RootInstaller.CANDIDATES.size, RootInstaller.CANDIDATES.distinct().size)
    }
}
