package dev.wystore.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Keeps the enabled launcher `<activity-alias>` in step with the chosen [AccentPreset].
 *
 * `MainActivity` itself stays enabled without a `LAUNCHER` filter so explicit intents — from
 * notifications, installer callbacks, tests and `adb` — always resolve. The nine launcher entries
 * are aliases of `MainActivity`, one per [AccentPreset], with `.LauncherOrange` enabled in the
 * manifest and the other eight disabled.
 */
object LauncherIconPolicy {

    data class ComponentTransition(
        val className: String,
        val newState: Int
    )

    fun aliasClassName(preset: AccentPreset): String = when (preset) {
        AccentPreset.ORANGE -> "dev.wystore.LauncherOrange"
        AccentPreset.RED -> "dev.wystore.LauncherRed"
        AccentPreset.PINK -> "dev.wystore.LauncherPink"
        AccentPreset.VIOLET -> "dev.wystore.LauncherViolet"
        AccentPreset.INDIGO -> "dev.wystore.LauncherIndigo"
        AccentPreset.BLUE -> "dev.wystore.LauncherBlue"
        AccentPreset.TEAL -> "dev.wystore.LauncherTeal"
        AccentPreset.GREEN -> "dev.wystore.LauncherGreen"
        AccentPreset.AMBER -> "dev.wystore.LauncherAmber"
    }

    fun isCurrentlyEnabled(preset: AccentPreset, currentState: Int): Boolean = when (currentState) {
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> preset == AccentPreset.ORANGE
        else -> false
    }

    /**
     * Enables the chosen alias before disabling any previous one so the launcher never sees a
     * moment with no enabled entry, which would drop a pinned shortcut from the home screen.
     * Aliases already in the right state are left untouched so routine startups and non-accent
     * settings saves do not broadcast `ACTION_PACKAGE_CHANGED`.
     */
    fun transitions(
        target: AccentPreset,
        currentStates: (AccentPreset) -> Int
    ): List<ComponentTransition> {
        val result = mutableListOf<ComponentTransition>()
        if (!isCurrentlyEnabled(target, currentStates(target))) {
            result += ComponentTransition(
                className = aliasClassName(target),
                newState = PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            )
        }
        for (preset in AccentPreset.entries) {
            if (preset != target && isCurrentlyEnabled(preset, currentStates(preset))) {
                result += ComponentTransition(
                    className = aliasClassName(preset),
                    newState = PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                )
            }
        }
        return result
    }
}

object LauncherIconSwitcher {

    /**
     * Called from `WyStoreApplication.onCreate` and `MainActivity.onStop`, never on the tap inside
     * Appearance settings: when the task was launched through the old alias, disabling that alias
     * makes `ActivityTaskManagerService` finish the running Activity immediately even with
     * [PackageManager.DONT_KILL_APP]. Waiting until `onStop` lets the user try any number of
     * swatches in place and switches the icon as they leave for the home screen.
     */
    fun sync(context: Context, accent: AccentPreset) {
        runCatching {
            val pm = context.packageManager
            val pkg = context.packageName
            val changes = LauncherIconPolicy.transitions(accent) { preset ->
                pm.getComponentEnabledSetting(ComponentName(pkg, LauncherIconPolicy.aliasClassName(preset)))
            }
            for (change in changes) {
                pm.setComponentEnabledSetting(
                    ComponentName(pkg, change.className),
                    change.newState,
                    PackageManager.DONT_KILL_APP
                )
            }
        }
    }
}
