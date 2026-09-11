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
 * Resolved by component rather than by build property. The user's own Xiaomi runs HyperOS with an
 * empty `ro.miui.ui.version.name` and no security-centre package at all, while the phone in the
 * bug report is a MIUI build that has both: what matters is whether the screen is there, and that
 * is a question with a real answer.
 */
object VendorBackgroundSettings {

    /**
     * Candidate screens, tried in order. Each is a vendor's own activity and may be absent, renamed
     * or unexported on any given build, which is why nothing here is assumed to work.
     */
    private val CANDIDATES = listOf(
        // Xiaomi: MIUI autostart. The one that matters for the report this was written for.
        ComponentName(
            "com.miui.securitycenter",
            "com.miui.permcenter.autostart.AutoStartManagementActivity"
        ),
        // Xiaomi: MIUI per-app battery policy, separate again from Android's exemption.
        ComponentName(
            "com.miui.powerkeeper",
            "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
        ),
        // Huawei
        ComponentName(
            "com.huawei.systemmanager",
            "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
        ),
        // Oppo / realme
        ComponentName(
            "com.coloros.safecenter",
            "com.coloros.safecenter.permission.startup.StartupAppListActivity"
        ),
        // vivo
        ComponentName(
            "com.vivo.permissionmanager",
            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
        )
    )

    /** The first screen this device actually has, or null when the vendor adds no rules of its own. */
    fun intentFor(context: Context): Intent? {
        val packageManager = context.applicationContext.packageManager
        return CANDIDATES.firstNotNullOfOrNull { component ->
            val intent = Intent().apply {
                this.component = component
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            intent.takeIf {
                runCatching { packageManager.resolveActivity(it, 0) }.getOrNull() != null
            }
        }
    }

    /** For the diagnostics report: which vendor screen was found, if any. */
    fun describe(context: Context): String {
        val intent = intentFor(context) ?: return "нет"
        return intent.component?.packageName ?: "есть"
    }
}
