package dev.wystore.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.wystore.R
import dev.wystore.data.InstallSource
import dev.wystore.data.InstalledApp
import dev.wystore.data.ManagedApp
import dev.wystore.data.PendingUpdate
import dev.wystore.ui.components.StateBadge
import dev.wystore.ui.components.WyCard
import dev.wystore.ui.components.WyDivider
import dev.wystore.ui.components.managedSourceLabel
import dev.wystore.ui.components.sourceLabel

/**
 * One installed app in the library.
 *
 * The card has three bands: identity, the switches that govern how the app is updated, and the
 * actions. The actions used to sit in a single fixed `Row`, so on a narrow screen the last two of
 * five buttons were pushed off the edge; a [FlowRow] wraps them instead.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryAppRow(
    app: InstalledApp,
    managed: ManagedApp?,
    pendingUpdate: PendingUpdate?,
    onAdopt: (InstalledApp) -> Unit,
    onUpdateManaged: (ManagedApp) -> Unit,
    onRequestForce: (ManagedApp) -> Unit,
    onRemoveManaged: (InstalledApp) -> Unit,
    onUninstall: (InstalledApp) -> Unit,
    onCheck: (ManagedApp) -> Unit,
    onOpenDetails: (ManagedApp) -> Unit,
    onLaunch: (String) -> Unit,
    onInstallPending: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    WyCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InstalledAppIcon(app, Modifier.size(52.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        app.label,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            app.versionName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                        StateBadge(
                            text = if (managed != null) managedSourceLabel(managed) else sourceLabel(app.source)
                        )
                    }
                    Text(
                        stringResource(R.string.library_updated_at, formatLastUpdated(app.lastUpdateTime)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(10.dp))
                // The step the user is most likely to want, kept on the identity line.
                if (pendingUpdate != null) {
                    Button(onClick = { onInstallPending(app.packageName) }) {
                        Text(stringResource(R.string.common_update), maxLines = 1)
                    }
                } else {
                    FilledTonalButton(onClick = { onLaunch(app.packageName) }) {
                        Text(stringResource(R.string.common_open), maxLines = 1)
                    }
                }
            }

            Text(
                app.packageName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (managed != null) {
                WyDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.library_auto_update),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = managed.autoUpdate,
                        onCheckedChange = { onUpdateManaged(managed.copy(autoUpdate = it)) }
                    )
                }
                if (app.source == InstallSource.GOOGLE_PLAY) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.library_force_wystore),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = managed.forceWyStore,
                            onCheckedChange = { enabled ->
                                if (enabled) onRequestForce(managed) else onUpdateManaged(managed.copy(forceWyStore = false))
                            }
                        )
                    }
                }
            }

            WyDivider()
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (managed == null) {
                    TextButton(onClick = { onAdopt(app) }) {
                        Text(stringResource(R.string.dialog_adopt_confirm))
                    }
                } else {
                    TextButton(onClick = { onOpenDetails(managed) }) {
                        Text(stringResource(R.string.library_open_page))
                    }
                    if (pendingUpdate == null) {
                        TextButton(onClick = { onCheck(managed) }) {
                            Text(stringResource(R.string.common_check))
                        }
                    }
                    TextButton(onClick = { onRemoveManaged(app) }) {
                        Text(stringResource(R.string.library_remove))
                    }
                }
                TextButton(onClick = { onUninstall(app) }) {
                    Text(stringResource(R.string.dialog_uninstall_confirm))
                }
            }
        }
    }
}
