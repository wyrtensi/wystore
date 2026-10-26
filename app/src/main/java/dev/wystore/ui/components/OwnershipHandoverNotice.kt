package dev.wystore.ui.components

import android.content.Context
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.wystore.R
import dev.wystore.data.InstalledApp
import dev.wystore.data.OwnershipHandover
import dev.wystore.data.OwnershipHandoverPolicy
import dev.wystore.updates.SessionInstallSupport

fun ownershipHandover(context: Context, app: InstalledApp): OwnershipHandover =
    OwnershipHandoverPolicy.decide(
        Build.VERSION.SDK_INT, app.updateOwnerPackageName, context.packageName,
        !SessionInstallSupport(context).sessionsRefused()
    )

@Composable
fun OwnershipHandoverNotice(handover: OwnershipHandover, modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier) {
    val resource = when (handover) {
        OwnershipHandover.UNSUPPORTED_ANDROID -> R.string.ownership_unsupported_android
        OwnershipHandover.UNSUPPORTED_INSTALLER -> R.string.ownership_unsupported_installer
        OwnershipHandover.REINSTALL -> null
        OwnershipHandover.OWNED -> R.string.ownership_transfer_confirmed
    }
    if (resource == null) {
        OwnershipTransferConsequences(modifier)
    } else {
        Text(stringResource(resource), modifier = modifier, style = MaterialTheme.typography.bodySmall)
    }
}
