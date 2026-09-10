package dev.wystore.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.wystore.R
import dev.wystore.selfupdate.SelfUpdateChecker

/**
 * The name of whoever Android holds responsible for an app's updates.
 *
 * The store only ever showed "Google Play" or "another source", which answers neither of the two
 * questions worth asking about an installed app: who updates it now, and can Wy Store take that
 * over. A package name is not an answer either, so it is resolved to the label its own app
 * carries and falls back to the package only when that app is gone.
 */
@Composable
fun installerLabel(installerPackageName: String?): String {
    val context = LocalContext.current
    val ownPackage = context.packageName
    return when {
        installerPackageName.isNullOrBlank() -> stringResource(R.string.details_owner_none)
        // The app's own name, spelled the way it is everywhere else - the label the package
        // manager returns is the same string, but reading it back would depend on this package
        // being visible to itself under every install state.
        installerPackageName == ownPackage -> SelfUpdateChecker.APP_LABEL
        else -> runCatching {
            val info = context.packageManager.getApplicationInfo(installerPackageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: installerPackageName
    }
}
