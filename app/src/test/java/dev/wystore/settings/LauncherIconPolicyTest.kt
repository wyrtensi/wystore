package dev.wystore.settings

import android.content.pm.PackageManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherIconPolicyTest {

    @Test
    fun everyAccentHasItsOwnAlias() {
        val names = AccentPreset.entries.map(LauncherIconPolicy::aliasClassName)
        assertEquals(AccentPreset.entries.size, names.toSet().size)
    }

    @Test
    fun freshInstallOnOrangeTouchesNothing() {
        val changes = LauncherIconPolicy.transitions(AccentPreset.ORANGE) {
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        }
        assertTrue(changes.isEmpty())
    }

    @Test
    fun switchingFromDefaultEnablesTargetBeforeDisablingOrange() {
        val changes = LauncherIconPolicy.transitions(AccentPreset.TEAL) {
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        }
        assertEquals(
            listOf(
                LauncherIconPolicy.ComponentTransition(
                    "dev.wystore.LauncherTeal",
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                ),
                LauncherIconPolicy.ComponentTransition(
                    "dev.wystore.LauncherOrange",
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                )
            ),
            changes
        )
    }

    @Test
    fun alreadySwitchedAccentTouchesNothing() {
        val state = mapOf(
            AccentPreset.ORANGE to PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            AccentPreset.TEAL to PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        )
        val changes = LauncherIconPolicy.transitions(AccentPreset.TEAL) { preset ->
            state[preset] ?: PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        }
        assertTrue(changes.isEmpty())
    }

    @Test
    fun switchingBackToOrangeEnablesOrangeBeforeDisablingPrevious() {
        val state = mapOf(
            AccentPreset.ORANGE to PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            AccentPreset.VIOLET to PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        )
        val changes = LauncherIconPolicy.transitions(AccentPreset.ORANGE) { preset ->
            state[preset] ?: PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        }
        assertEquals(
            listOf(
                LauncherIconPolicy.ComponentTransition(
                    "dev.wystore.LauncherOrange",
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                ),
                LauncherIconPolicy.ComponentTransition(
                    "dev.wystore.LauncherViolet",
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                )
            ),
            changes
        )
    }
}
