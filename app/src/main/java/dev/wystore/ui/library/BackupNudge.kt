package dev.wystore.ui.library

import dev.wystore.ui.components.HubTextAction
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import dev.wystore.R
import dev.wystore.data.BackupLocation
import dev.wystore.ui.components.WyCard

/**
 * Keeps an uninstall of Wy Store from costing the library.
 *
 * Updating Wy Store keeps everything. Removing it does not: Android deletes the list of accepted
 * apps with the app's data and clears Wy Store as the installer of every app it installed, so
 * nothing can be rebuilt by asking the system afterwards. The kept-current backup file covers
 * that, but it lived at the bottom of Settings where nobody moving over from RuStore would look.
 *
 * With apps accepted and no backup file, this offers to start one. With nothing accepted - which
 * is what a reinstall looks like - it offers to restore one. Either can be put away for good.
 */
@Composable
fun BackupNudge(
    hasAccepted: Boolean,
    onExportUri: (android.net.Uri) -> Unit,
    onImportUri: (android.net.Uri) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var kept by remember { mutableStateOf(BackupLocation(context).uri != null) }
    val key = if (hasAccepted) KEY_SAVE_DISMISSED else KEY_RESTORE_DISMISSED
    var dismissed by remember(key) { mutableStateOf(prefs.getBoolean(key, false)) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            onExportUri(uri)
            kept = true
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImportUri(uri)
    }
    if (dismissed || (hasAccepted && kept)) return

    WyCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(if (hasAccepted) R.string.library_backup_save_title else R.string.library_backup_restore_title),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                stringResource(if (hasAccepted) R.string.library_backup_save_text else R.string.library_backup_restore_text),
                style = MaterialTheme.typography.bodySmall
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HubTextAction(onClick = {
                    prefs.edit { putBoolean(key, true) }
                    dismissed = true
                }) { Text(stringResource(R.string.library_backup_later), maxLines = 1) }
                Button(onClick = {
                    if (hasAccepted) {
                        // The same single name Settings uses: the file is rewritten from now on.
                        exportLauncher.launch("wystore_backup.json")
                    } else {
                        importLauncher.launch(arrayOf("application/json", "*/*"))
                    }
                }) {
                    Text(
                        stringResource(if (hasAccepted) R.string.library_backup_save else R.string.library_backup_restore),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private const val PREFS = "wy_store_library_hints"
private const val KEY_SAVE_DISMISSED = "backup_save_dismissed"
private const val KEY_RESTORE_DISMISSED = "backup_restore_dismissed"
