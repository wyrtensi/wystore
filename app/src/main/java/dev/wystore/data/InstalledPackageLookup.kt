package dev.wystore.data

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build

/**
 * Package lookups that also see apps frozen in a work profile.
 *
 * Shelter and other profile managers freeze an app by hiding it (DevicePolicyManager
 * .setApplicationHidden). Android then leaves it out of every plain lookup, so a frozen app read
 * as uninstalled: the update check skipped it and nothing was offered until the user unfroze it by
 * hand. It is still installed, and an update installs over it and leaves it frozen, so it is asked
 * for with [SEE_FROZEN].
 *
 * That flag also returns apps removed with "keep data", which are not installed at all; those are
 * told apart by [isOnDevice].
 */
object InstalledPackageLookup {

    const val SEE_FROZEN = PackageManager.MATCH_UNINSTALLED_PACKAGES

    /** Installed for this user - frozen or not - as opposed to only having its data kept. */
    fun isOnDevice(applicationFlags: Int?): Boolean =
        applicationFlags != null && applicationFlags and ApplicationInfo.FLAG_INSTALLED != 0

    /**
     * Frozen: found only once hidden packages are asked for. Shown as such, because a frozen app
     * cannot be opened until it is unfrozen.
     */
    fun isFrozen(visibleWithoutFlag: Boolean, onDevice: Boolean): Boolean = onDevice && !visibleWithoutFlag

    /** The installed package, frozen included, or null when it is not on the device. */
    fun packageInfo(packageManager: PackageManager, packageName: String, flags: Int = 0): PackageInfo? =
        runCatching { query(packageManager, packageName, flags or SEE_FROZEN) }
            .getOrNull()
            ?.takeIf { isOnDevice(it.applicationInfo?.flags) }

    fun versionCode(packageManager: PackageManager, packageName: String): Long? =
        packageInfo(packageManager, packageName)?.longVersionCode

    fun isInstalled(packageManager: PackageManager, packageName: String): Boolean =
        packageInfo(packageManager, packageName) != null

    /**
     * The app's own icon, frozen apps included. A plain icon lookup misses a frozen app, so its
     * row in My apps went blank.
     */
    fun icon(packageManager: PackageManager, packageName: String): android.graphics.drawable.Drawable? =
        runCatching { packageManager.getApplicationIcon(packageName) }.getOrNull()
            ?: runCatching { packageInfo(packageManager, packageName)?.applicationInfo?.loadIcon(packageManager) }.getOrNull()

    /** Whether a plain lookup, the kind a frozen app is hidden from, finds the package. */
    fun visibleWithoutFlag(packageManager: PackageManager, packageName: String): Boolean =
        runCatching { query(packageManager, packageName, 0) }.isSuccess

    private fun query(packageManager: PackageManager, packageName: String, flags: Int): PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, flags)
        }
}
