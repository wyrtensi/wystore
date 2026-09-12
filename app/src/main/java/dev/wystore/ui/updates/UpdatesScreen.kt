package dev.wystore.ui.updates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import dev.wystore.R
import dev.wystore.InstallQueueItem
import dev.wystore.InstallQueueStatus
import dev.wystore.UpdateCheckTask
import dev.wystore.data.CheckProblemReason
import dev.wystore.data.InstalledApp
import dev.wystore.data.ManagedApp
import dev.wystore.data.PendingUpdate
import dev.wystore.data.UpdateCheckSummary
import dev.wystore.ui.components.LocalBottomBarInset
import dev.wystore.ui.components.EmptyState
import dev.wystore.ui.components.AppIcon
import androidx.compose.ui.text.style.TextOverflow
import dev.wystore.ui.components.ScreenPadding
import dev.wystore.ui.components.SectionHeader
import dev.wystore.ui.components.WyCard
import dev.wystore.ui.components.queueStatusLabel
import dev.wystore.ui.library.UpdateCheckStatusCard
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesScreen(
    modifier: Modifier = Modifier,
    managed: List<ManagedApp>,
    installed: List<InstalledApp>,
    pendingUpdates: List<PendingUpdate>,
    updateCheckTask: UpdateCheckTask?,
    lastUpdateCheck: UpdateCheckSummary?,
    queue: List<InstallQueueItem> = emptyList(),
    packageIcons: Map<String, String> = emptyMap(),
    onOpen: (ManagedApp) -> Unit,
    onCheckUpdates: () -> Unit = {},
    /**
     * Whether a check will also hand what it finds to the installer, so the button can say so.
     *
     * It is the same switch that governs it - "install as soon as it is downloaded" - rather than
     * a second one beside it: two settings for one behaviour would eventually disagree. The button
     * only reports what is already true.
     */
    installsWhatItFinds: Boolean = false,
    onUpdateAll: () -> Unit = {},
    onInstallPending: (String) -> Unit,
    onDiscardPending: (String) -> Unit,
    onQueueRetry: (String) -> Unit = {},
    onQueueCancel: (String) -> Unit = {},
    onQueueSkip: (String) -> Unit = {},
    onQueueDownload: (String) -> Unit = {},
    onQueueDiscard: (String) -> Unit = {},
    /** Asks to remove the installed copy so this row can be installed in its place. */
    onQueueReplace: (InstallQueueItem) -> Unit = {},
    /** Opens the "the source, not you" dialog for a failure nothing on this phone can fix. */
    onSourceFailureHelp: () -> Unit = {},
    onConfirmUnverifiedSource: (String) -> Unit = {},
    /** Stops a running transfer and keeps the bytes it has. */
    onQueuePause: (String) -> Unit = {},
    /** Carries a paused transfer on from the bytes on disk. */
    onQueueResume: (String) -> Unit = {},
    onStartQueue: () -> Unit = {}
) {
    val installedByPackage = remember(installed) { installed.associateBy { it.packageName } }
    val orderedManaged = remember(managed, installedByPackage) {
        managed.sortedByDescending { managedApp ->
            installedByPackage[managedApp.packageName]?.lastUpdateTime ?: managedApp.lastUpdatedAt ?: 0L
        }
    }
    val pendingByPackage = remember(pendingUpdates) { pendingUpdates.associateBy { it.packageName } }
    // Items that are transferring, waiting, failed or canceled have no other home in the UI;
    // without this section a failed download is invisible and unrecoverable.
    val actionableQueue = remember(queue) {
        queue.filter { item ->
            when (item.status) {
                InstallQueueStatus.DOWNLOADING,
                InstallQueueStatus.VERIFYING,
                InstallQueueStatus.RESOLVING,
                InstallQueueStatus.QUEUED,
                InstallQueueStatus.CANCELED,
                InstallQueueStatus.FAILED -> true
                else -> false
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.updates_title)) },
                actions = {
                    // The only way to ask for a check used to be the Library tab, which is not
                    // where anyone looks for it.
                    TextButton(onClick = onCheckUpdates, enabled = updateCheckTask?.running != true) {
                        Text(
                            stringResource(
                                when {
                                    updateCheckTask?.running == true -> R.string.library_checking
                                    installsWhatItFinds -> R.string.updates_check_and_install
                                    else -> R.string.updates_check_now
                                }
                            )
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding()),
            contentPadding = PaddingValues(
                start = ScreenPadding,
                end = ScreenPadding,
                top = 4.dp,
                bottom = 24.dp + LocalBottomBarInset.current
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    stringResource(R.string.updates_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            updateCheckTask?.takeIf { it.active }?.let { task ->
                item { UpdateCheckStatusCard(task) }
            }

            lastUpdateCheck?.let { summary ->
                item { LastUpdateCheckCard(summary) }
            }

            if (actionableQueue.isNotEmpty()) {
                item {
                    val canStart = actionableQueue.any { it.status == InstallQueueStatus.QUEUED }
                    SectionHeader(
                        title = stringResource(R.string.updates_queue_title, actionableQueue.size),
                        actionLabel = if (canStart) stringResource(R.string.updates_queue_start) else null,
                        onActionClick = if (canStart) onStartQueue else null
                    )
                }
                items(actionableQueue, key = { "queue:${it.id}" }) { item ->
                    QueueItemCard(
                        item = item,
                        iconUrl = packageIcons[item.packageName],
                        onRetry = { onQueueRetry(item.id) },
                        onCancel = { onQueueCancel(item.id) },
                        onSkip = { onQueueSkip(item.id) },
                        onDownload = { onQueueDownload(item.id) },
                        onDiscard = { onQueueDiscard(item.id) },
                        onReplace = { onQueueReplace(item) },
                        onSourceFailureHelp = onSourceFailureHelp,
                        onConfirmUnverifiedSource = { onConfirmUnverifiedSource(item.packageName) },
                        onPause = { onQueuePause(item.id) },
                        onResume = { onQueueResume(item.id) }
                    )
                }
            }

            if (pendingUpdates.isNotEmpty()) {
                item {
                    // Installing everything already downloaded lived on Home, and in the Library it
                    // lived in a label only. This is the screen those downloads are listed on, so it
                    // was the one place the action was missing.
                    SectionHeader(
                        title = stringResource(R.string.updates_ready_title, pendingUpdates.size),
                        actionLabel = stringResource(R.string.updates_install_all),
                        onActionClick = onUpdateAll
                    )
                }
                items(pendingUpdates, key = { "pending:${it.packageName}" }) { pending ->
                    PendingUpdateCard(
                        update = pending,
                        iconUrl = packageIcons[pending.packageName],
                        onInstallPending = onInstallPending,
                        onDiscardPending = onDiscardPending
                    )
                }
            }

            if (orderedManaged.isNotEmpty()) {
                item {
                    SectionHeader(title = stringResource(R.string.updates_managed_title, orderedManaged.size))
                }
                items(orderedManaged, key = { it.packageName }) { managedApp ->
                    val local = installedByPackage[managedApp.packageName]
                    val pending = pendingByPackage[managedApp.packageName]
                    UpdateStatusRow(
                        managed = managedApp,
                        installed = local,
                        pendingUpdate = pending,
                        onOpen = { onOpen(managedApp) },
                        onAction = {
                            if (pending != null) onInstallPending(pending.packageName)
                            else onOpen(managedApp)
                        }
                    )
                }
            } else if (pendingUpdates.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.CheckCircle,
                        title = stringResource(R.string.home_all_up_to_date),
                        message = stringResource(R.string.updates_managed_empty)
                    )
                }
            }
        }
    }
}

@Composable
fun PendingUpdateCard(
    update: PendingUpdate,
    iconUrl: String? = null,
    onInstallPending: (String) -> Unit,
    onDiscardPending: (String) -> Unit = {}
) {
    val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
    WyCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(
                model = iconUrl,
                contentDescription = null,
                size = 44.dp,
                fallbackPackageName = update.packageName
            )
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    update.label.ifBlank { update.packageName },
                    style = MaterialTheme.typography.titleSmall,
                    color = onContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // One line, and only the version. The screen's own text above already says that
                // Android asks for confirmation; repeated under every card on a narrow screen it
                // wrapped into four lines of the same sentence.
                Text(
                    stringResource(R.string.updates_downloaded_version, update.versionName),
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = { onInstallPending(update.packageName) },
                // Tighter than the default so the name and the version keep their room on a narrow
                // screen once the discard button is standing next to it.
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = onContainer,
                    contentColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(stringResource(R.string.common_install), maxLines = 1)
            }
            // A downloaded APK used to be a one-way street: install it or live with it.
            IconButton(
                onClick = { onDiscardPending(update.packageName) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.updates_discard_pending),
                    modifier = Modifier.size(20.dp),
                    tint = onContainer
                )
            }
        }
    }
}

@Composable
fun LastUpdateCheckCard(summary: UpdateCheckSummary) {
    WyCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(
                    if (summary.manual) R.string.updates_last_check_manual
                    else R.string.updates_last_check_background
                ),
                style = MaterialTheme.typography.titleSmall
            )
            Text(formatCheckTime(summary.finishedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(summary.detail, style = MaterialTheme.typography.bodySmall)
            if (summary.total > 0) {
                Text(
                    stringResource(
                        R.string.updates_check_counts,
                        summary.checked,
                        summary.total,
                        summary.updates,
                        summary.problems
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // A count is not something anyone can act on. Which apps, and whether they could not
            // be reached just now or cannot be updated from here at all - those are different
            // problems with different answers, and the card used to give the same number to both.
            summary.problemApps.take(PROBLEMS_SHOWN).forEach { problem ->
                Text(
                    stringResource(
                        when (problem.reason) {
                            CheckProblemReason.SIGNATURE_CHANGED -> R.string.check_problem_signature
                            CheckProblemReason.NOT_IN_SOURCE -> R.string.check_problem_not_in_source
                            CheckProblemReason.UNREACHABLE -> R.string.check_problem_unreachable
                        },
                        problem.label
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (summary.problemApps.size > PROBLEMS_SHOWN) {
                Text(
                    stringResource(
                        R.string.check_problem_more,
                        summary.problemApps.size - PROBLEMS_SHOWN
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

fun formatCheckTime(time: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(time))

/**
 * One durable queue row with the actions its state actually allows. Retry is only meaningful once
 * the item has stopped, and cancel only while it is still moving.
 */
@Composable
fun QueueItemCard(
    item: InstallQueueItem,
    iconUrl: String? = null,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onSkip: () -> Unit,
    onDownload: () -> Unit = {},
    onDiscard: () -> Unit = {},
    onReplace: () -> Unit = {},
    onSourceFailureHelp: () -> Unit = {},
    onConfirmUnverifiedSource: () -> Unit = {},
    onPause: () -> Unit = {},
    onResume: () -> Unit = {}
) {
    val stopped = item.status == InstallQueueStatus.FAILED || item.status == InstallQueueStatus.CANCELED
    val failed = item.status == InstallQueueStatus.FAILED
    // The row said "uninstall it and install it again" and offered Retry, which does the same
    // thing again and fails the same way. A signature that does not match is the one failure a
    // retry can never get past, and the only way through it is the removal - so it is a button.
    // The source would not vouch for its own file. Answerable by the user, and the one signature
    // failure removing the installed app cannot help with: nothing here is about the installed
    // copy, so "uninstall and install" was advice that cost an app and changed nothing.
    val unconfirmed = failed &&
        dev.wystore.updates.UnverifiedSourceConsent.isAnswerable(item.errorCode, item.detail)
    val needsReplace = failed &&
        item.errorCode == dev.wystore.updates.model.QueueErrorCode.SIGNATURE &&
        !unconfirmed
    // A failure the user cannot retry their way out of, because it is not theirs. Offered as one
    // more line on the row rather than a banner: it belongs to this app, not to the whole screen.
    val sourceProblem = failed &&
        dev.wystore.updates.SourceFailurePolicy.looksLikeSourceProblem(item.errorCode)
    WyCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = if (failed) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(
                    model = iconUrl,
                    contentDescription = null,
                    size = 40.dp,
                    fallbackPackageName = item.packageName
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        item.label.ifBlank { item.packageName },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.label.isNotBlank() && item.label != item.packageName) {
                        Text(
                            item.packageName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                // A row that has stopped had no way off this screen. Skip and cancel both leave it
                // sitting there as "canceled" with "Retry" as the only thing on offer, so an item
                // the user had finished with stayed in the queue for good.
                if (stopped) {
                    IconButton(onClick = onDiscard) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.queue_discard)
                        )
                    }
                }
            }
            Text(
                queueStatusLabel(item),
                style = MaterialTheme.typography.bodySmall,
                color = if (failed) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            item.progress?.takeIf { it.totalBytes > 0 }?.let { progress ->
                LinearProgressIndicator(
                    progress = { progress.fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.extraSmall)
                )
            }
            if (sourceProblem) {
                TextButton(
                    onClick = onSourceFailureHelp,
                    modifier = Modifier.align(Alignment.End)
                ) { Text(stringResource(R.string.source_failure_help)) }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                when {
                    unconfirmed -> {
                        TextButton(onClick = onRetry) { Text(stringResource(R.string.common_retry)) }
                        Spacer(Modifier.width(4.dp))
                        Button(onClick = onConfirmUnverifiedSource) {
                            Text(stringResource(R.string.unverified_source_confirm))
                        }
                    }
                    needsReplace -> {
                        TextButton(onClick = onRetry) { Text(stringResource(R.string.common_retry)) }
                        Spacer(Modifier.width(4.dp))
                        Button(onClick = onReplace) { Text(stringResource(R.string.queue_replace)) }
                    }
                    stopped -> Button(onClick = onRetry) { Text(stringResource(R.string.common_retry)) }
                    // An item merely waiting in line can be started on its own, rather than only
                    // by starting the whole queue.
                    item.status == InstallQueueStatus.QUEUED -> {
                        TextButton(onClick = onSkip) { Text(stringResource(R.string.common_skip)) }
                        Spacer(Modifier.width(4.dp))
                        Button(onClick = onDownload) { Text(stringResource(R.string.queue_download_now)) }
                    }
                    // A transfer running now can be stopped without losing what it has fetched.
                    item.status == InstallQueueStatus.DOWNLOADING -> {
                        OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel)) }
                        Spacer(Modifier.width(4.dp))
                        Button(onClick = onPause) { Text(stringResource(R.string.common_pause)) }
                    }
                    item.status == InstallQueueStatus.PAUSED -> {
                        OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel)) }
                        Spacer(Modifier.width(4.dp))
                        Button(onClick = onResume) { Text(stringResource(R.string.common_resume)) }
                    }
                    else -> {
                        TextButton(onClick = onSkip) { Text(stringResource(R.string.common_skip)) }
                        Spacer(Modifier.width(4.dp))
                        OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel)) }
                    }
                }
            }
        }
    }
}

/** Enough to name the trouble without turning the card into a list. */
private const val PROBLEMS_SHOWN = 4
