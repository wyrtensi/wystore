package dev.wystore.permissions

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Opens the screen where installing apps is allowed for Wy Store.
 *
 * Some TV and vendor builds have no activity for the per-app screen, and starting it threw out of
 * the first install tap and closed the app. Each fallback is a screen from which the switch can
 * still be reached. Answers false when none of them exists.
 */
fun Context.openUnknownSourcesSettings(): Boolean {
    val packageUri = Uri.parse("package:$packageName")
    val candidates = listOf(
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, packageUri),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri),
        Intent(Settings.ACTION_SECURITY_SETTINGS)
    )
    return candidates.any { intent ->
        try {
            if (this !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}
