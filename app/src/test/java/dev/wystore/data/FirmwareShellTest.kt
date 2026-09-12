package dev.wystore.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FirmwareShellTest {

    private fun describe(vararg props: Pair<String, String>) =
        FirmwareShell.describe { name -> props.toMap()[name].orEmpty() }

    @Test
    fun `a device without MIUI says so`() {
        assertEquals("нет признаков MIUI", describe())
    }

    @Test
    fun `an unset switch is reported as MIUI's default, not as off`() {
        assertEquals(
            "MIUI V12, оптимизация MIUI: не задана (по умолчанию вкл)",
            describe("ro.miui.ui.version.name" to "V12")
        )
    }

    @Test
    fun `a switched off optimisation is reported as off`() {
        assertEquals(
            "MIUI V125, оптимизация MIUI: выкл",
            describe("ro.miui.ui.version.name" to "V125", "persist.sys.miui_optimization" to "false")
        )
    }

    @Test
    fun `HyperOS is named as itself`() {
        assertEquals(
            "HyperOS OS2.0, оптимизация MIUI: вкл",
            describe(
                "ro.miui.ui.version.name" to "V816",
                "ro.mi.os.version.name" to "OS2.0",
                "persist.sys.miui_optimization" to "true"
            )
        )
    }
}
