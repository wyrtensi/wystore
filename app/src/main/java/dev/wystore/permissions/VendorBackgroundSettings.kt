package dev.wystore.permissions

import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * The screen a vendor keeps its own background rules behind, when there is one.
 *
 * Android has an answer for "may this app work in the background": the battery-optimisation
 * exemption, which [PermissionRepository] reads and can ask for. On several manufacturers that
 * answer is not the whole truth. MIUI keeps a second list - autostart - and an app missing from it
 * has its scheduled work dropped no matter what Android's own setting says; the app cannot read
 * that list, cannot ask to be added to it, and gets no error when work is silently not run. From
 * inside the app it looks exactly like a schedule being ignored, which is how it reaches us.
 *
 * So this does the only honest thing available: finds out whether such a screen exists on this
 * device, and can open it. No state is claimed, because none can be read - a card that guessed
 * "on" or "off" here would be guessing.
 *
 * Found by asking the system what it can open, never by reading a build property or a brand. Two
 * Xiaomi phones settle that: one runs MIUI and has both screens, the other is the same
 * manufacturer running a custom AOSP build with no MIUI property and no security-centre package
 * at all. "Is this a Xiaomi" has the same answer for both and tells us nothing; "can this device
 * open an autostart screen" has opposite answers, and is the question worth asking.
 *
 * Actions come before class names for the same reason. On a MIUI device the autostart screen
 * resolves as the default handler for `miui.intent.action.OP_AUTO_START`, and an action outlives
 * the renaming or moving of a class between shell versions.
 */
object VendorBackgroundSettings {

    /**
     * Vendor actions, tried first. Both MIUI entries were read off a MIUI device: the autostart
     * screen and the per-app battery screen each declare theirs with a DEFAULT category, so an
     * implicit intent reaches them.
     */
    private val ACTIONS = listOf(
        "miui.intent.action.OP_AUTO_START",
        "miui.intent.action.HIDDEN_APPS_CONFIG_ACTIVITY"
    )

    /**
     * Explicit screens, tried when no action answers. Only the MIUI pair has been checked against
     * a device; the rest are the names those shells are reported to use, and are here on the same
     * terms as everything else - resolved before use, so a name that is wrong, renamed or gone
     * means no card rather than a dead button.
     */
    private val COMPONENTS = listOf(
        ComponentName(
            "com.miui.securitycenter",
            "com.miui.permcenter.autostart.AutoStartManagementActivity"
        ),
        ComponentName(
            "com.miui.powerkeeper",
            "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
        ),
        ComponentName(
            "com.huawei.systemmanager",
            "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
        ),
        ComponentName(
            "com.coloros.safecenter",
            "com.coloros.safecenter.permission.startup.StartupAppListActivity"
        ),
        ComponentName(
            "com.vivo.permissionmanager",
            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
        )
    )

    /** The first screen this device actually has, or null when the vendor adds no rules of its own. */
    fun intentFor(context: Context): Intent? {
        val packageManager = context.applicationContext.packageManager
        val candidates = ACTIONS.map { action ->
            Intent(action).addCategory(Intent.CATEGORY_DEFAULT)
        } + COMPONENTS.map { component ->
            Intent().setComponent(component)
        }
        return candidates.firstOrNull { intent ->
            runCatching { packageManager.resolveActivity(intent, 0) }.getOrNull() != null
        }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** For the diagnostics report: which vendor screen was found, if any. */
    fun describe(context: Context): String {
        val intent = intentFor(context) ?: return "нет"
        return intent.action ?: intent.component?.packageName ?: "есть"
    }
}
