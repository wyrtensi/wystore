package dev.wystore.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import dev.wystore.R
import dev.wystore.ui.components.WyCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupSettingsSection(
    managedCount: Int,
    githubCount: Int,
    onExportUri: (android.net.Uri) -> Unit,
    onImportUri: (android.net.Uri, Boolean) -> Unit,
    onExportJson: () -> String,
    onRestoreJson: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var showTextBackupDialog by remember { mutableStateOf(false) }
    var textBackupContent by remember { mutableStateOf("") }
    var mergeMode by remember { mutableStateOf(true) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) onExportUri(uri)
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImportUri(uri, mergeMode)
    }

    WyCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(stringResource(R.string.backup_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.backup_counts, managedCount, githubCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                        exportLauncher.launch("wystore_backup_$timestamp.json")
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.backup_export))
                }
                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        importLauncher.launch(arrayOf("application/json", "*/*"))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.backup_import))
                }
            }

            TextButton(
                onClick = {
                    textBackupContent = onExportJson()
                    showTextBackupDialog = true
                },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(stringResource(R.string.backup_text_link))
            }
        }
    }

    if (showTextBackupDialog) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        AlertDialog(
            onDismissRequest = { showTextBackupDialog = false },
            title = { Text(stringResource(R.string.backup_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.backup_dialog_hint), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = textBackupContent,
                        onValueChange = { textBackupContent = it },
                        modifier = Modifier.fillMaxWidth().height(180.dp),
                        label = { Text(stringResource(R.string.backup_json_label)) },
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.backup_merge), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        Switch(checked = mergeMode, onCheckedChange = { mergeMode = it })
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val clip = ClipData.newPlainText("WyStore Backup", textBackupContent)
                                clipboard?.setPrimaryClip(clip)
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.backup_copy))
                        }
                        OutlinedButton(
                            onClick = {
                                val text = clipboard?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                                if (text.isNotBlank()) {
                                    textBackupContent = text
                                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.backup_paste))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (textBackupContent.isNotBlank()) {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        onRestoreJson(textBackupContent, mergeMode)
                        showTextBackupDialog = false
                    }
                }) { Text(stringResource(R.string.backup_restore)) }
            },
            dismissButton = { TextButton(onClick = { showTextBackupDialog = false }) { Text(stringResource(R.string.common_close)) } }
        )
    }
}
