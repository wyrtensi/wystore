package dev.wystore.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.wystore.ui.components.WySpinner
import dev.wystore.InstallQueueItem
import androidx.compose.runtime.rememberCoroutineScope
import dev.wystore.data.DiagnosticsCollector
import dev.wystore.data.DiagnosticsReport
import kotlinx.coroutines.launch
import dev.wystore.R
import dev.wystore.data.PendingUpdate
import dev.wystore.data.StoreSettings
import dev.wystore.localization.StatusTextResolver
import dev.wystore.selfupdate.SelfUpdateChecker
import dev.wystore.selfupdate.SelfUpdateStatus
import dev.wystore.ui.components.CompactSettingSwitch
import dev.wystore.ui.components.FactRow
import dev.wystore.ui.components.PackageUiStateReducer
import dev.wystore.ui.components.PrimaryAction
import dev.wystore.ui.components.ScreenPadding
import dev.wystore.ui.components.SectionHeader
import dev.wystore.ui.components.SectionSpacing
import dev.wystore.ui.components.StatusCode
import dev.wystore.ui.components.TransferProgress
import dev.wystore.ui.components.WyCard
import dev.wystore.ui.components.WyDivider

/**
 * Wy Store's own page: which build is installed, where it came from, and whether a newer one is
 * published. Updating happens through the ordinary queue, so it is verified and confirmed exactly
 * like an update to any other app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSettingsScreen(
    settings: StoreSettings,
    status: SelfUpdateStatus,
    versionName: String,
    versionCode: Long,
    onUpdateSettings: (StoreSettings) -> Unit,
    onCheck: () -> Unit,
    onInstall: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    queueItem: InstallQueueItem? = null,
    pendingUpdate: PendingUpdate? = null,
    onInstallDownloaded: () -> Unit = {},
    onCancelDownload: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val resources = LocalResources.current

    // After Wy Store updates itself the process restarts and the check result is gone, so this
    // page opened silent - no line at all about the build the user had just installed. Opening it
    // is the request; the answer should be here by the time it is read.
    LaunchedEffect(Unit) {
        if (status == SelfUpdateStatus.Idle) onCheck()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_hub_about_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(start = ScreenPadding, end = ScreenPadding, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(SectionSpacing)
        ) {
            WyCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Wy Store", style = MaterialTheme.typography.titleMedium)
                    // Says plainly what this is and what it is not: reading RuStore's catalogue
                    // without saying so invites the assumption that RuStore stands behind it.
                    Text(
                        stringResource(R.string.about_independent),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    WyDivider()
                    FactRow(stringResource(R.string.about_version), "$versionName ($versionCode)")
                    FactRow(stringResource(R.string.about_source), SelfUpdateChecker.PROJECT_URL)
                    WyDivider()
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, SelfUpdateChecker.PROJECT_URL.toUri())
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.about_source)) }
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, SelfUpdateChecker.DISCLAIMER_URL.toUri())
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.about_disclaimer_link)) }
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
                    WyDivider()
                    // The answers to the first three questions anyone asks about a bug - which
                    // Android, which device, is there root - plus what the queue actually did.
                    // Collected on demand rather than kept: root has to be asked, not remembered.
                    Text(
                        stringResource(R.string.about_diagnostics_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    DiagnosticsButton()
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(
                    title = stringResource(R.string.about_check),
                    subtitle = stringResource(R.string.about_signature_note)
                )
                WyCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Wy Store updates through the same queue as every other app, so its own
                        // download was visible only on Updates and in the shade — not on the page
                        // the user pressed "check" on. The queue's state is shown here too.
                        val transfer = remember(queueItem, pendingUpdate, resources) {
                            if (queueItem == null && pendingUpdate == null) {
                                null
                            } else {
                                // A finished row stays in the queue, and the reducer has no
                                // catalogue entry to fall back on here, so it reads a completed
                                // update as "not installed". Only a run still in progress or
                                // waiting for the user says anything true on this page.
                                PackageUiStateReducer.reduce(
                                    queueItem = queueItem,
                                    pendingUpdate = pendingUpdate,
                                    resources = resources
                                ).takeIf { it.status.code in QUEUE_STATES }
                            }
                        }
                        val working = transfer != null &&
                            transfer.primaryAction != PrimaryAction.Install &&
                            transfer.primaryAction != PrimaryAction.Retry

                        if (transfer != null) {
                            Text(
                                text = StatusTextResolver.resolve(context, transfer.status),
                                style = MaterialTheme.typography.bodyMedium,
                                color = when (transfer.status.code) {
                                    StatusCode.FAILED_NETWORK,
                                    StatusCode.FAILED_STORAGE,
                                    StatusCode.FAILED_SIGNATURE,
                                    StatusCode.FAILED_GENERIC -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                            // A transfer whose size the server has not stated yet has neither a
                            // fraction nor byte counts, and the card sat on "0%" with nothing
                            // moving. An indeterminate bar is the honest answer while it lasts.
                            if (transfer.progress != null ||
                                transfer.transferInfo != null ||
                                transfer.status.code == StatusCode.DOWNLOADING
                            ) {
                                TransferProgress(
                                    progress = transfer.progress,
                                    transferInfo = transfer.transferInfo
                                )
                            }
                        } else when (status) {
                            SelfUpdateStatus.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                                WySpinner(size = 18.dp, strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    stringResource(R.string.about_checking),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            SelfUpdateStatus.UpToDate -> Text(
                                stringResource(R.string.about_up_to_date),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            is SelfUpdateStatus.Available -> Text(
                                stringResource(R.string.about_available, status.versionName),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            is SelfUpdateStatus.Failed -> Text(
                                stringResource(R.string.about_failed, status.reason),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            SelfUpdateStatus.Idle -> Unit
                        }

                        // Downloaded and verified: the only step left is Android's own dialog.
                        if (transfer?.primaryAction == PrimaryAction.Install) {
                            Button(onClick = onInstallDownloaded, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.common_install))
                            }
                        } else if (!working && status is SelfUpdateStatus.Available) {
                            Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.about_install))
                            }
                        }
                        // Changing your mind used to mean leaving for the Updates tab.
                        if (working && queueItem != null) {
                            OutlinedButton(
                                onClick = { onCancelDownload(queueItem.id) },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(stringResource(R.string.common_cancel)) }
                        }
                        FilledTonalButton(
                            onClick = onCheck,
                            enabled = status != SelfUpdateStatus.Checking && !working,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.about_check)) }
                    }
                }
            }

            WyCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CompactSettingSwitch(
                        label = stringResource(R.string.about_auto_title),
                        checked = settings.selfUpdateEnabled
                    ) { onUpdateSettings(settings.copy(selfUpdateEnabled = it)) }
                    Text(
                        stringResource(R.string.about_auto_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * What the update card is allowed to report. Anything else means the queue has nothing to say
 * about Wy Store right now, and the self-update check speaks instead.
 */
private val QUEUE_STATES = setOf(
    StatusCode.QUEUED,
    StatusCode.DOWNLOADING,
    StatusCode.VERIFYING,
    StatusCode.READY_TO_INSTALL,
    StatusCode.AWAITING_UNKNOWN_SOURCES_PERMISSION,
    StatusCode.AWAITING_USER_CONFIRMATION,
    StatusCode.INSTALLING,
    StatusCode.FAILED_NETWORK,
    StatusCode.FAILED_STORAGE,
    StatusCode.FAILED_SIGNATURE,
    StatusCode.FAILED_GENERIC
)

/**
 * Hands over a plain-text report about this install.
 *
 * One action, because there is only one thing to do with it: the share sheet carries the report as
 * a file, ready to attach to an issue. Everything the report has to say is inside that file and
 * nothing is copied alongside it.
 *
 * The report is built when the button is pressed. It asks the system whether root is there, and an
 * answer from an hour ago would be worse than none.
 */
@Composable
private fun DiagnosticsButton() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val failedMessage = stringResource(R.string.about_diagnostics_failed)
    var notice by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    OutlinedButton(
        onClick = {
            if (busy) return@OutlinedButton
            busy = true
            scope.launch {
                notice = runCatching {
                    val text = DiagnosticsReport.render(
                        DiagnosticsCollector(context).collect(),
                        System.currentTimeMillis()
                    )
                    context.startActivity(Intent.createChooser(shareIntent(context, text), null))
                }.fold(onSuccess = { null }, onFailure = { failedMessage })
                busy = false
            }
        },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth()
    ) { Text(stringResource(R.string.about_diagnostics)) }

    notice?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun shareIntent(context: android.content.Context, text: String): Intent {
    val directory = java.io.File(context.cacheDir, "diagnostics").apply { mkdirs() }
    val file = java.io.File(directory, "wystore-diagnostics.txt")
    file.writeText(text)
    val uri = androidx.core.content.FileProvider.getUriForFile(
        context,
        "${context.packageName}.pending-updates",
        file
    )
    return Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        // The file and nothing else. The same text used to travel as EXTRA_TEXT so a chat that
        // takes no attachments would still get something, but a report worth reading is long, and
        // targets that accept both pasted the whole of it into the message box beside the file.
        // One attachment is what gets sent, so one attachment is what is offered.
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
