package dev.wystore.data

import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The diagnostics report has to name every setting, not the ones that mattered the day it was
 * written.
 *
 * The first version of that dump listed thirteen of them. Everything added afterwards - the quiet
 * hours, the per-category notification switches, the artifact retention - was simply absent from
 * every report anyone sent, and nothing said so. The dump is written by hand on purpose, so what a
 * reader sees is what the code says; this test is what makes the hand-written list keep up. Add a
 * field to [StoreSettings] and it fails here until the report learns to print it.
 */
class StoreSettingsCoverageTest {

    @Test
    fun theReportPrintsEverySetting() {
        val declared = StoreSettings::class.java.declaredFields
            .filter { !it.isSynthetic && !Modifier.isStatic(it.modifiers) }
            .map { it.name }
            .toSet()

        val printed = DiagnosticsSettingsDump.of(StoreSettings()).map { (key, _) -> key }.toSet()

        assertEquals(
            "every StoreSettings field must appear in the diagnostics report",
            emptySet<String>(),
            declared - printed
        )
        assertEquals(
            "the diagnostics report must not invent settings that do not exist",
            emptySet<String>(),
            printed - declared
        )
    }
}
