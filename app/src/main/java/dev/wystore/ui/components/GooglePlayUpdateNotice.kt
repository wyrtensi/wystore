package dev.wystore.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.wystore.R
import dev.wystore.data.GooglePlayUpdatePolicy
import dev.wystore.data.InstalledApp
import dev.wystore.data.ManagedApp

/** Keeps the actual pause state and its safe recovery path together on every app surface. */
@Composable
fun GooglePlayUpdateNotice(
    managedApp: ManagedApp?,
    installedApp: InstalledApp?,
    continueChecksAfterGooglePlayUpdate: Boolean,
    onOpenUpdateSettings: () -> Unit,
    modifier: Modifier = Modifier,
    action: @Composable (String, () -> Unit) -> Unit = { label, onClick ->
        HubTextAction(onClick = onClick) { Text(label) }
    }
) {
    if (managedApp == null || !GooglePlayUpdatePolicy.hasGooglePlayUpdate(managedApp, installedApp)) return
    val paused = GooglePlayUpdatePolicy.isPaused(managedApp, installedApp, continueChecksAfterGooglePlayUpdate)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(if (paused) R.string.google_play_pause_reason else R.string.google_play_continue_reason),
            style = MaterialTheme.typography.bodySmall,
            color = if (paused) StatusTone.PAUSED.color() ?: MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
        )
        action(stringResource(R.string.google_play_update_settings), onOpenUpdateSettings)
    }
}
