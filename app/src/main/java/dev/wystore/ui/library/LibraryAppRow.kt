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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.clickable
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
import dev.wystore.ui.components.sourceBadgeLabel

/**
 * One installed app in the library.
 *
 * A screen of full-height cards - identity, package name, two switches and a row of five buttons
 * each - fitted three apps on the display. The row is one line until it is opened: icon, name, what
 * it is, and the single action the user is most likely to want. Everything else appears on tap.
 *
 * Tapping the icon opens the app's page in the store rather than expanding the row, which is the
 * gesture the same icon already performs everywhere else in the app.
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
    onOpenStorePage: (String) -> Unit,
    onLaunch: (String) -> Unit,
    onInstallPending: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable(app.packageName) { mutableStateOf(false) }
    WyCard(modifier = modifier.fillMaxWidth(), onClick = { expanded = !expanded }) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InstalledAppIcon(
                    app,
                    Modifier
                        .size(44.dp)
                        .clickable { onOpenStorePage(app.packageName) }
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                        // The badge is measured first, at whatever short word it needs, and the
                        // version takes the rest. The other way round a version like
                        // "1.423.966222932" took the whole line and left the badge one character
                        // wide, which wrapped it into a column of letters and stretched the row
                        // to half a screen.
                        Text(
                            app.versionName,
                            modifier = Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        StateBadge(
                            text = if (managed != null) managedSourceLabel(managed) else sourceBadgeLabel(app.source)
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
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
                Icon(
                    imageVector = if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                    contentDescription = stringResource(
                        if (expanded) R.string.library_collapse else R.string.library_expand
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!expanded) return@Column

            Text(
                app.packageName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                stringResource(R.string.library_updated_at, formatLastUpdated(app.lastUpdateTime)),
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
