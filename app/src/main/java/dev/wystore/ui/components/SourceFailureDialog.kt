package dev.wystore.ui.components

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.compose.foundation.layout.fillMaxWidth
import dev.wystore.R
import dev.wystore.data.DiagnosticsCollector
import dev.wystore.data.DiagnosticsReport
import dev.wystore.selfupdate.SelfUpdateChecker
import dev.wystore.selfupdate.SelfUpdateStatus
import kotlinx.coroutines.launch

/**
 * What to do when the source, not the phone, is what failed.
 *
 * RuStore is read through its public pages and they change without notice. When they do, every
 * download fails at once for everybody, and the only two useful moves are to take a build that
 * speaks the new shape, or to say it is broken so that build gets written. Until now the queue
 * said "download failed" and left it there, which invites the user to blame their connection and
 * retry forever.
 *
 * So the dialog offers exactly those two, in that order, and the report to attach to the second.
 * The report leaves as a file the user saves themselves - the same rule the share sheet follows,
 * and the reason nothing here puts its contents on the clipboard.
 */
@Composable
fun SourceFailureDialog(
    status: SelfUpdateStatus,
    onCheckSelfUpdate: () -> Unit,
    onInstallSelfUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var notice by remember { mutableStateOf<String?>(null) }
    val savedMessage = stringResource(R.string.source_failure_report_saved)
    val failedMessage = stringResource(R.string.about_diagnostics_failed)

    // Asked as the dialog opens: the answer is the whole point of it, and making the user press
    // one button to learn whether the other one is worth pressing helps nobody.
    LaunchedEffect(Unit) {
        if (status == SelfUpdateStatus.Idle) onCheckSelfUpdate()
    }

    val saveReport = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            notice = runCatching {
                val text = DiagnosticsReport.render(
                    DiagnosticsCollector(context).collect(),
                    System.currentTimeMillis()
                )
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(text.toByteArray())
                }
            }.fold(onSuccess = { savedMessage }, onFailure = { failedMessage })
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.source_failure_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.source_failure_body))
                Text(
                    when (status) {
                        SelfUpdateStatus.Checking -> stringResource(R.string.about_checking)
                        is SelfUpdateStatus.Available ->
                            stringResource(R.string.source_failure_update_available, status.versionName)
                        is SelfUpdateStatus.Failed -> stringResource(R.string.about_failed, status.reason)
                        // No newer build: the fix is not out yet, so the useful move is the report.
                        else -> stringResource(R.string.source_failure_no_update)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (status is SelfUpdateStatus.Available) {
                    OutlinedButton(
                        onClick = onInstallSelfUpdate,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.about_install)) }
                }
                OutlinedButton(
                    onClick = { saveReport.launch(REPORT_FILE_NAME) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.source_failure_save_report)) }
                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, SelfUpdateChecker.ISSUES_URL.toUri())
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.about_issues_link)) }
                notice?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
        }
    )
}

private const val REPORT_FILE_NAME = "wystore-diagnostics.txt"
