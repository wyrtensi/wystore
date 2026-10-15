package dev.wystore.data

/**
 * Short names for the installers a library row is likely to meet.
 *
 * The labels the stores give themselves are too long for a badge: "Google Play Store" squeezed the
 * version next to it down to "8.…". And Android's own package installer is not a store at all - it
 * is what installs an APK someone opened by hand, so it reads as manual like an app with no
 * installer recorded.
 */
object InstallerNames {

    private val SHORT = mapOf(
        "com.android.vending" to "Google Play",
        "ru.vk.store" to "RuStore",
        "com.xiaomi.mipicks" to "GetApps",
        // Xiaomi's own updater for the apps it ships; its label varies by firmware.
        "com.xiaomi.discover" to "Xiaomi",
        "com.xiaomi.market" to "GetApps",
        "com.huawei.appmarket" to "AppGallery",
        "com.sec.android.app.samsungapps" to "Galaxy Store",
        "com.amazon.venezia" to "Amazon",
        "org.fdroid.fdroid" to "F-Droid",
        "com.looker.droidify" to "Droid-ify",
        "dev.imranr.obtainium" to "Obtainium",
        "com.aurora.store" to "Aurora Store"
    )

    private val PACKAGE_INSTALLERS = setOf(
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.miui.packageinstaller",
        "com.samsung.android.packageinstaller",
        "com.android.shell"
    )

    fun short(installerPackageName: String?): String? = installerPackageName?.let(SHORT::get)

    /** Nobody installed it on the user's behalf: an APK opened by hand, adb, or nothing recorded. */
    fun isManual(installerPackageName: String?): Boolean =
        installerPackageName.isNullOrBlank() || installerPackageName in PACKAGE_INSTALLERS

    /**
     * It came with the phone. Xiaomi ships apps outside the system image - the calendar on
     * /product/data-app with no installer, partner apps on /cust with "android" - and both read as
     * installed by hand, which nobody did.
     */
    fun isPreinstalled(installerPackageName: String?, sourceDir: String?): Boolean =
        installerPackageName == SYSTEM ||
            (installerPackageName.isNullOrBlank() && sourceDir != null && !sourceDir.startsWith("/data/"))

    private const val SYSTEM = "android"
}
