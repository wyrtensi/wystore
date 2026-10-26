package dev.wystore.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.wystore.R
import dev.wystore.data.InstalledApp
import dev.wystore.data.OwnershipHandover
import dev.wystore.data.OwnershipTransferVersionPolicy
import dev.wystore.data.TransferVersionWarning

/** Identity and consequences only: the host dialog owns scrolling and the destructive action. */
@Composable
fun OwnershipTransferDetails(
    installed: InstalledApp,
    targetVersion: String?,
    handover: OwnershipHandover,
    modifier: Modifier = Modifier,
    targetVersionCode: Long? = null,
    assetName: String? = null,
    targetIsReleaseTag: Boolean = false
) {
    val version = targetVersion?.takeIf { it.isNotBlank() } ?: stringResource(R.string.common_no_data)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(installed.label, style = MaterialTheme.typography.titleMedium)
        Text(installed.packageName, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (handover == OwnershipHandover.REINSTALL) {
            Text(stringResource(R.string.search_installed_version, installed.versionName),
                style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(
                if (targetIsReleaseTag) R.string.ownership_transfer_release else R.string.ownership_transfer_target_version,
                version
            ), style = MaterialTheme.typography.bodyMedium)
            assetName?.takeIf { it.isNotBlank() }?.let {
                Text(stringResource(R.string.ownership_transfer_file, it),
                    style = MaterialTheme.typography.bodySmall)
            }
            val warning = OwnershipTransferVersionPolicy.warning(
                installed.versionName, installed.versionCode, targetVersion, targetVersionCode, targetIsReleaseTag
            )
            val warningResource = when (warning) {
                TransferVersionWarning.APK_DOWNGRADE -> R.string.ownership_transfer_downgrade
                TransferVersionWarning.OLDER_RELEASE -> R.string.ownership_transfer_older_release
                TransferVersionWarning.NONE -> null
            }
            warningResource?.let {
                Text(stringResource(it), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error)
            }
            key(installed.packageName) {
                OwnershipTransferConsequences()
            }
        } else {
            OwnershipHandoverNotice(handover)
        }
    }
}

/** The same loss warning and optional caveat are used before source selection and final consent. */
@Composable
fun OwnershipTransferConsequences(modifier: Modifier = Modifier) {
    var showProfileLimits by rememberSaveable { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.ownership_transfer_data_loss),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        Text(stringResource(R.string.ownership_transfer_verification),
            style = MaterialTheme.typography.bodySmall)
        HubTextAction(onClick = { showProfileLimits = !showProfileLimits }) {
            Text(stringResource(
                if (showProfileLimits) R.string.ownership_transfer_less else R.string.ownership_transfer_more
            ))
        }
        if (showProfileLimits) {
            Text(stringResource(R.string.ownership_transfer_profile_limits),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
