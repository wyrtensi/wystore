package dev.wystore.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.wystore.R
import dev.wystore.data.InstalledApp
import dev.wystore.data.InstallerNames
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
fun installerLabel(installerPackageName: String?): String =
    if (InstallerNames.isManual(installerPackageName)) stringResource(R.string.details_owner_none)
    else installerName(installerPackageName) ?: stringResource(R.string.details_owner_none)

/**
 * The same name, short enough for a badge on a list row: "Manual" where nobody installed it.
 *
 * The library badge used to name where Wy Store looks for updates, so once apps were accepted
 * every row read "RuStore" or "GitHub" and the one fact the list could not otherwise show - which
 * store actually installed each app - was hidden in the opened row.
 */
@Composable
fun installerLabel(app: InstalledApp): String =
    if (app.preinstalled) stringResource(R.string.installer_preinstalled)
    else installerLabel(app.installerPackageName)

@Composable
fun installerShortLabel(app: InstalledApp): String =
    if (app.preinstalled) stringResource(R.string.installer_preinstalled_short)
    else installerShortLabel(app.installerPackageName)

@Composable
fun installerShortLabel(installerPackageName: String?): String =
    InstallerNames.short(installerPackageName)
        ?: if (InstallerNames.isManual(installerPackageName)) stringResource(R.string.installer_manual_short)
        else installerName(installerPackageName) ?: stringResource(R.string.installer_manual_short)

@Composable
private fun installerName(installerPackageName: String?): String? {
    val context = LocalContext.current
    // Each row asks; the package manager lookup is not something to repeat on every frame.
    return remember(installerPackageName, context) {
        val ownPackage = context.packageName
        when {
            installerPackageName.isNullOrBlank() -> null
            // The app's own name, spelled the way it is everywhere else - the label the package
            // manager returns is the same string, but reading it back would depend on this
            // package being visible to itself under every install state.
            installerPackageName == ownPackage -> SelfUpdateChecker.APP_LABEL
            else -> runCatching {
                val info = context.packageManager.getApplicationInfo(installerPackageName, 0)
                context.packageManager.getApplicationLabel(info).toString()
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: installerPackageName
        }
    }
}
