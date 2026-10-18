package dev.wystore.ui.settings

import dev.wystore.ui.components.fadingTopEdge
import dev.wystore.ui.components.color
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import dev.wystore.ui.components.HubPageHeader
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
import androidx.compose.material3.FilledTonalButton
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
import dev.wystore.ui.components.LocalBottomBarInset
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
            // The concept's page header: a round back button, where the page belongs as the
            // accent line, the title in plain weight.
            HubPageHeader(
                title = stringResource(R.string.settings_hub_about_title),
                eyebrow = stringResource(R.string.settings_title),
                onBack = onBack
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = contentPadding.calculateTopPadding()).fadingTopEdge()
                .verticalScroll(rememberScrollState())
                .padding(start = ScreenPadding, end = ScreenPadding, top = 20.dp, bottom = 28.dp + LocalBottomBarInset.current),
            verticalArrangement = Arrangement.spacedBy(SectionSpacing)
        ) {
            // Who this is and whether it is current, in one panel: the version and the check sat
            // in two cards a screen apart, so "is mine the latest?" took scrolling to answer.
            dev.wystore.ui.components.HubPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    dev.wystore.ui.components.AppIcon(
                        model = null,
                        contentDescription = null,
                        size = 56.dp,
                        fallbackPackageName = context.packageName
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Wy Store",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                            )
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            dev.wystore.ui.components.HubTag("$versionName ($versionCode)")
                            dev.wystore.ui.components.HubTag("GitHub", tint = dev.wystore.ui.components.sourceTint("GitHub"))
                        }
                    }
                }

                // Wy Store updates through the same queue as every other app, so its own download
                // was visible only on Updates and in the shade - not on the page the user pressed
                // "check" on. The queue's state is shown here too.
                val transfer = remember(queueItem, pendingUpdate, resources) {
                    if (queueItem == null && pendingUpdate == null) {
                        null
                    } else {
                        // A finished row stays in the queue, and the reducer has no catalogue entry
                        // to fall back on here, so it reads a completed update as "not installed".
                        // Only a run still in progress or waiting for the user says anything true.
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

                Spacer(Modifier.height(4.dp))
                if (transfer != null) {
                    Text(
                        text = StatusTextResolver.resolve(context, transfer.status),
                        style = MaterialTheme.typography.bodyMedium,
                        color = dev.wystore.ui.components.StatusTones.of(transfer.status.code).color()
                            ?: MaterialTheme.colorScheme.onSurface
                    )
                    // A transfer whose size the server has not stated yet has neither a fraction
                    // nor byte counts; an indeterminate bar is the honest answer while it lasts.
                    if (transfer.progress != null ||
                        transfer.transferInfo != null ||
                        transfer.status.code == StatusCode.DOWNLOADING
                    ) {
                        TransferProgress(progress = transfer.progress, transferInfo = transfer.transferInfo)
                    }
                } else when (status) {
                    SelfUpdateStatus.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                        WySpinner(size = 18.dp, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.about_checking), style = MaterialTheme.typography.bodyMedium)
                    }
                    SelfUpdateStatus.UpToDate -> Text(
                        stringResource(R.string.about_up_to_date),
                        style = MaterialTheme.typography.bodyMedium,
                        color = dev.wystore.ui.components.StatusTone.READY.color() ?: MaterialTheme.colorScheme.onSurface
                    )
                    is SelfUpdateStatus.Available -> Text(
                        stringResource(R.string.about_available, status.versionName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
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
                    dev.wystore.ui.components.HubWideButton(
                        text = stringResource(R.string.common_install),
                        onClick = onInstallDownloaded,
                        primary = true
                    )
                } else if (!working && status is SelfUpdateStatus.Available) {
                    dev.wystore.ui.components.HubWideButton(
                        text = stringResource(R.string.about_install),
                        onClick = onInstall,
                        primary = true
                    )
                }
                // Changing your mind used to mean leaving for the Updates tab.
                if (working && queueItem != null) {
                    dev.wystore.ui.components.HubWideButton(
                        text = stringResource(R.string.common_cancel),
                        onClick = { onCancelDownload(queueItem.id) },
                        chevron = false
                    )
                } else if (status != SelfUpdateStatus.Checking) {
                    dev.wystore.ui.components.HubWideButton(
                        text = stringResource(R.string.about_check),
                        onClick = onCheck,
                        chevron = false
                    )
                }
                Text(
                    stringResource(R.string.about_signature_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                WyDivider()
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

            // Says plainly what this is and what it is not: reading RuStore's catalogue without
            // saying so invites the assumption that RuStore stands behind it.
            dev.wystore.ui.components.HubNote(
                text = stringResource(R.string.about_independent),
                icon = Icons.Outlined.Info
            )

            // The links as one menu of rows, as the settings menu is, instead of three wide grey
            // buttons stacked one on another.
            WyCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    val open: (String) -> Unit = { url ->
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
                    }
                    AboutLinkRow(
                        title = stringResource(R.string.about_source),
                        subtitle = SelfUpdateChecker.PROJECT_URL.removePrefix("https://"),
                        icon = Icons.Outlined.Home,
                        tint = androidx.compose.ui.graphics.Color(0xFFC59BF2)
                    ) { open(SelfUpdateChecker.PROJECT_URL) }
                    WyDivider(modifier = Modifier.padding(start = 66.dp, end = 16.dp))
                    AboutLinkRow(
                        title = stringResource(R.string.about_disclaimer_link),
                        subtitle = null,
                        icon = Icons.Outlined.Info,
                        tint = androidx.compose.ui.graphics.Color(0xFF6CC7F0)
                    ) { open(SelfUpdateChecker.DISCLAIMER_URL) }
                    WyDivider(modifier = Modifier.padding(start = 66.dp, end = 16.dp))
                    AboutLinkRow(
                        title = stringResource(R.string.about_issues_link),
                        subtitle = null,
                        icon = Icons.Outlined.Warning,
                        tint = androidx.compose.ui.graphics.Color(0xFFF2C94C)
                    ) { open(SelfUpdateChecker.ISSUES_URL) }
                }
            }

            // The answers to the first three questions anyone asks about a bug - which Android,
            // which device, is there root - plus what the queue actually did. Collected on demand
            // rather than kept: root has to be asked, not remembered.
            WyCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.about_diagnostics_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    DiagnosticsButton()
                }
            }
        }
    }
}

/** One link of the About menu: a coloured glyph in a tile, its name, and a chevron. */
@Composable
private fun AboutLinkRow(
    title: String,
    subtitle: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    val tint = dev.wystore.ui.components.glyphTint(tint)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(38.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
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

    androidx.compose.material3.Button(
        onClick = {
            if (busy) return@Button
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
