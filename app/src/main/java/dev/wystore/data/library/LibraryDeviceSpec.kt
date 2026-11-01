package dev.wystore.data.library

import android.app.ActivityManager
import android.content.Context
import android.os.Build

data class LibraryDeviceSpec(
    val sdk: Int, val abis: List<String>, val density: Int, val locales: List<String>,
    val features: Set<String>, val glEsVersion: Int
) {
    companion object {
        fun current(context: Context): LibraryDeviceSpec {
            val locales = context.resources.configuration.locales
            return LibraryDeviceSpec(Build.VERSION.SDK_INT, Build.SUPPORTED_ABIS.toList(),
                context.resources.displayMetrics.densityDpi,
                (0 until locales.size()).map { locales[it].toLanguageTag().replace('-', '_') },
                context.packageManager.systemAvailableFeatures.mapNotNull { it.name }.toSet(),
                context.getSystemService(ActivityManager::class.java).deviceConfigurationInfo.reqGlEsVersion)
        }
    }
}
