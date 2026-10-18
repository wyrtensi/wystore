package dev.wystore.ui.updates

import dev.wystore.ui.components.HubTextAction
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.text.font.FontWeight
import dev.wystore.ui.components.HubGroupTitle
import dev.wystore.ui.components.color
import dev.wystore.ui.components.fadingTopEdge
import dev.wystore.ui.components.HubHeader
import dev.wystore.ui.components.HubHeaderPill
import dev.wystore.ui.components.HubNote
import dev.wystore.ui.components.HubPanel
import dev.wystore.ui.components.HubWideButton
import dev.wystore.ui.components.groupPosition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import dev.wystore.rowFor
import dev.wystore.UpdateCheckTask
import dev.wystore.background.CheckOutcome
import dev.wystore.background.UpdateCheckReport
import dev.wystore.data.CheckProblemReason
import dev.wystore.data.GitHubRepository
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
    /** Opens an app's page by package name, for a check problem that has to be answered there. */
    onOpenPackage: (String) -> Unit = {},
    /** Stops a running transfer and keeps the bytes it has. */
    onQueuePause: (String) -> Unit = {},
    /** Carries a paused transfer on from the bytes on disk. */
    onQueueResume: (String) -> Unit = {},
    onStartQueue: () -> Unit = {},
    /** Brings back Android's dialog for an install waiting on it off screen. */
    onShowInstallDialog: (String) -> Unit = {},
    onCancelAwaitingInstall: (String) -> Unit = {},
    /**
     * Opens the page of a queued or downloaded app: package, name and the repository a GitHub row
     * names, since such a row's package is a placeholder until its file is verified.
     */
    onOpenAppPage: (String, String, GitHubRepository?) -> Unit = { _, _, _ -> },
    /** Shows the source's notes for the version waiting here; same arguments as [onOpenAppPage]. */
    onWhatsNew: (String, String, GitHubRepository?) -> Unit = { _, _, _ -> }
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
                // A paused download has Resume and Cancel below; left out here, it could only be
                // reached from a catalogue card.
                InstallQueueStatus.PAUSED,
                InstallQueueStatus.CANCELED,
                InstallQueueStatus.FAILED -> true
                // Only while Android's dialog waits off screen: the card is where its Cancel is.
                InstallQueueStatus.INSTALLING -> item.awaitingConfirmation
                else -> false
            }
        }
    }

    // Wy Hub's screen: no top bar - an accent line and a large plain title - then one panel saying
    // how things stand, then the lists. Rows of a list sit in one panel, so the list is laid out
    // without the gap that used to stand between every card; sections keep theirs.
    Column(modifier = modifier.fillMaxSize()) {
        HubHeader(
            title = stringResource(R.string.updates_title),
            eyebrow = "Wy Store",
            trailing = {
                // The only way to ask for a check used to be the Library tab, which is not where
                // anyone looks for it.
                HubHeaderPill(
                    text = stringResource(
                        when {
                            updateCheckTask?.running == true -> R.string.library_checking
                            installsWhatItFinds -> R.string.updates_check_and_install
                            else -> R.string.updates_check_now
                        }
                    ),
                    onClick = onCheckUpdates,
                    enabled = updateCheckTask?.running != true
                )
            }
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize().fadingTopEdge(),
            contentPadding = PaddingValues(
                start = ScreenPadding,
                end = ScreenPadding,
                // As deep as the fade, so at rest nothing sits in it.
                top = 20.dp,
                bottom = 24.dp + LocalBottomBarInset.current
            )
        ) {
            updateCheckTask?.takeIf { it.active }?.let { task ->
                item { Box(Modifier.padding(bottom = 12.dp)) { UpdateCheckStatusCard(task) } }
            }

            item {
                UpdatesHero(
                    summary = lastUpdateCheck,
                    ready = pendingUpdates.size,
                    waiting = actionableQueue.size,
                    onOpenApp = onOpenPackage,
                    onInstallAll = onUpdateAll,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            item {
                HubNote(
                    text = stringResource(R.string.updates_intro),
                    icon = Icons.Outlined.Info,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            if (actionableQueue.isNotEmpty()) {
                item {
                    val canStart = actionableQueue.any { it.status == InstallQueueStatus.QUEUED }
                    HubGroupTitle(
                        title = stringResource(R.string.updates_queue_title, actionableQueue.size),
                        meta = if (canStart) stringResource(R.string.updates_queue_start) else null,
                        onMetaClick = if (canStart) onStartQueue else null,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                items(actionableQueue, key = { "queue:${it.id}" }) { item ->
                    Box(Modifier.padding(bottom = 10.dp)) {
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
                            canReplace = dev.wystore.updates.ReplaceOfferPolicy.offersReplace(
                                errorCode = item.errorCode,
                                detail = item.detail,
                                appInstalled = installed.any { it.packageName == item.packageName }
                            ),
                            onPause = { onQueuePause(item.id) },
                            onResume = { onQueueResume(item.id) },
                            onShowInstallDialog = { onShowInstallDialog(item.id) },
                            onCancelAwaitingInstall = { onCancelAwaitingInstall(item.id) },
                            onOpen = { onOpenAppPage(item.packageName, item.label, item.githubRepository) },
                            onWhatsNew = { onWhatsNew(item.packageName, item.label, item.githubRepository) }
                        )
                    }
                }
                item { Spacer(Modifier.height(10.dp)) }
            }

            // An install waiting on Android's dialog has its card in the queue above, with its
            // Cancel; listed here as well it would be the same app twice.
            val readyUpdates = pendingUpdates.filterNot { queue.rowFor(it.packageName)?.awaitingConfirmation == true }
            if (readyUpdates.isNotEmpty()) {
                item {
                    // Installing everything already downloaded lived on Home, and in the Library it
                    // lived in a label only. This is the screen those downloads are listed on, so it
                    // was the one place the action was missing.
                    // "Install all" is the panel's button above; a second one here was the same
                    // action twice on one screen.
                    HubGroupTitle(
                        title = stringResource(R.string.updates_ready_title, readyUpdates.size),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                itemsIndexed(readyUpdates, key = { _, it -> "pending:${it.listKey}" }) { index, pending ->
                    PendingUpdateRow(
                        update = pending,
                        position = groupPosition(index, readyUpdates.size),
                        installing = queue.rowFor(pending.packageName)?.status == InstallQueueStatus.INSTALLING,
                        iconUrl = packageIcons[pending.packageName],
                        onInstallPending = onInstallPending,
                        onDiscardPending = onDiscardPending,
                        onOpen = { onOpenAppPage(pending.packageName, pending.label, pending.githubRepository) },
                        onWhatsNew = { onWhatsNew(pending.packageName, pending.label, pending.githubRepository) }
                    )
                }
                item { Spacer(Modifier.height(18.dp)) }
            }

            if (orderedManaged.isNotEmpty()) {
                item {
                    HubGroupTitle(
                        title = stringResource(R.string.updates_managed_group),
                        meta = orderedManaged.size.toString(),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                itemsIndexed(orderedManaged, key = { _, app -> app.packageName }) { index, managedApp ->
                    val local = installedByPackage[managedApp.packageName]
                    val pending = pendingByPackage[managedApp.packageName]
                    UpdateStatusRow(
                        managed = managedApp,
                        installed = local,
                        pendingUpdate = pending,
                        // Transfers have their own cards with their own actions in the queue section
                        // above; here only the install itself is shown, so the row stops offering
                        // Install while Android already has it.
                        installingItem = queue.rowFor(managedApp.packageName)
                            ?.takeIf { it.status == InstallQueueStatus.INSTALLING },
                        onOpen = { onOpen(managedApp) },
                        onAction = {
                            if (pending != null) onInstallPending(pending.packageName)
                            else onOpen(managedApp)
                        },
                        onWhatsNew = { onWhatsNew(managedApp.packageName, managedApp.label, null) },
                        groupPosition = groupPosition(index, orderedManaged.size)
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

/**
 * How things stand, in one sentence and one ring - the panel every Wy Hub screen opens with.
 *
 * It replaces the "last check" card, which led with a heading ("Last background check") and left
 * the answer to its third line.
 */
@Composable
private fun UpdatesHero(
    summary: UpdateCheckSummary?,
    ready: Int,
    waiting: Int,
    onOpenApp: (String) -> Unit,
    onInstallAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    HubPanel(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    when {
                        ready > 0 -> stringResource(R.string.updates_hero_ready, ready)
                        waiting > 0 -> stringResource(R.string.updates_hero_waiting, waiting)
                        summary != null && summary.problems > 0 ->
                            stringResource(R.string.updates_hero_problems, summary.problems)
                        else -> stringResource(R.string.home_all_up_to_date)
                    },
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Medium)
                )
                summary?.let {
                    Text(
                        stringResource(
                            if (it.manual) R.string.updates_hero_checked_manual else R.string.updates_hero_checked,
                            it.checked,
                            it.total,
                            formatCheckTime(it.finishedAt)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // The ring is about the check, so it stands beside a sentence about the check. Beside
            // "ready to install: 3" it read as "3 of 8" and said nothing about the three.
            summary?.takeIf { it.total > 0 && ready == 0 && waiting == 0 }?.let {
                Spacer(Modifier.width(14.dp))
                CheckRing(checked = it.checked, total = it.total)
            }
        }
        summary?.let { ProblemLines(it, onOpenApp) }
        if (ready > 0) {
            Spacer(Modifier.height(4.dp))
            HubWideButton(
                text = stringResource(R.string.updates_install_all),
                onClick = onInstallAll,
                primary = true
            )
        }
    }
}

/** The concept's ring: how much of what is looked after the last check reached. */
@Composable
private fun CheckRing(checked: Int, total: Int) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(56.dp)) {
        CircularProgressIndicator(
            progress = { (checked.toFloat() / total).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxSize(),
            strokeWidth = 5.dp,
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
            gapSize = 4.dp,
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
        Text(
            "$checked/$total",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}

/**
 * Which apps the check could not handle, and why. A count is not something anyone can act on: it
 * does not say which apps, nor whether they are unreachable now or cannot be updated from here.
 */
@Composable
private fun ProblemLines(summary: UpdateCheckSummary, onOpenApp: (String) -> Unit) {
    summary.problemApps.take(PROBLEMS_SHOWN).forEach { problem ->
        // A signature the source does not vouch for is answered on the app's page and nowhere
        // else, so the line that reports it leads there.
        val answerable = problem.reason == CheckProblemReason.SIGNATURE_CHANGED
        Text(
            stringResource(
                when (problem.reason) {
                    CheckProblemReason.SIGNATURE_CHANGED -> R.string.check_problem_signature
                    CheckProblemReason.NOT_IN_SOURCE -> R.string.check_problem_not_in_source
                    CheckProblemReason.UNREACHABLE -> R.string.check_problem_unreachable
                },
                problem.label
            ),
            modifier = if (answerable) {
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpenApp(problem.packageName) }
                    .padding(vertical = 4.dp)
            } else Modifier,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
    if (summary.problemApps.size > PROBLEMS_SHOWN) {
        Text(
            stringResource(R.string.check_problem_more, summary.problemApps.size - PROBLEMS_SHOWN),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * A downloaded update as one row of the "ready to install" group. The card it replaces was a
 * block of the accent's container colour per app, which in a list of three filled half the screen.
 */
@Composable
private fun PendingUpdateRow(
    update: PendingUpdate,
    position: dev.wystore.ui.components.GroupPosition,
    installing: Boolean,
    iconUrl: String?,
    onInstallPending: (String) -> Unit,
    onDiscardPending: (String) -> Unit,
    onOpen: () -> Unit,
    onWhatsNew: () -> Unit
) {
    val narrow = dev.wystore.ui.components.isNarrowScreen()
    val notes: @Composable () -> Unit = {
        IconButton(onClick = onWhatsNew, modifier = Modifier.size(if (narrow) 30.dp else 40.dp)) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = stringResource(R.string.details_changelog),
                modifier = Modifier.size(19.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    // A downloaded APK used to be a one-way street: install it or live with it.
    val discard: @Composable () -> Unit = {
        IconButton(onClick = { onDiscardPending(update.packageName) }, modifier = Modifier.size(if (narrow) 30.dp else 40.dp)) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.updates_discard_pending),
                modifier = Modifier.size(19.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    dev.wystore.ui.components.HubGroupRow(position = position, onClick = onOpen) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = if (narrow) 10.dp else 4.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(model = iconUrl, contentDescription = null, size = 40.dp, fallbackPackageName = update.packageName)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    update.label.ifBlank { update.packageName },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    dev.wystore.ui.components.HubTag(
                        dev.wystore.ui.components.shortVersionName(update.versionName) ?: update.versionName,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    // On a narrow screen the two small controls move under the name, so the name is
                    // not left two words wide between them and the button.
                    if (narrow) {
                        Spacer(Modifier.width(6.dp))
                        notes()
                        discard()
                    }
                }
            }
            if (!narrow) notes()
            // Grey: "Install all" in the panel above is the step the screen is asking for, and a
            // column of accent buttons under it shouted the same thing four times.
            FilledTonalButton(
                onClick = { onInstallPending(update.packageName) },
                enabled = !installing,
                contentPadding = dev.wystore.ui.components.rowButtonPadding()
            ) {
                Text(
                    stringResource(if (installing) R.string.common_installing else R.string.common_install),
                    maxLines = 1
                )
            }
            if (!narrow) discard()
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
    /** Whether removing the installed app would get this row past its failure - see ReplaceOfferPolicy. */
    canReplace: Boolean = false,
    onPause: () -> Unit = {},
    onResume: () -> Unit = {},
    onShowInstallDialog: () -> Unit = {},
    onCancelAwaitingInstall: () -> Unit = {},
    /** The app's page: a row in the queue had no way to it, and so no way to its notes either. */
    onOpen: () -> Unit = {},
    onWhatsNew: (() -> Unit)? = null
) {
    val stopped = item.status == InstallQueueStatus.FAILED || item.status == InstallQueueStatus.CANCELED
    val failed = item.status == InstallQueueStatus.FAILED
    // The row said "uninstall it and install it again" and offered Retry, which does the same
    // thing again and fails the same way. A signature that does not match is the one failure a
    // retry can never get past, and the only way through it is the removal - so it is a button.
    // The source would not vouch for its own file. Answerable by the user, and never the same row
    // as the one below: nothing about it is about the installed copy.
    val unconfirmed = failed &&
        dev.wystore.updates.UnverifiedSourceConsent.isAnswerable(item.errorCode, item.detail)
    val needsReplace = failed && canReplace
    // A failure the user cannot retry their way out of, because it is not theirs. Offered as one
    // more line on the row rather than a banner: it belongs to this app, not to the whole screen.
    val sourceProblem = failed &&
        dev.wystore.updates.SourceFailurePolicy.looksLikeSourceProblem(item.errorCode)
    // One surface for every state. A failed row used to be an error-coloured block; the Wy Hub
    // way is a coloured word, so the failure is told by its line rather than by painting the card.
    WyCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpen
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
                    // The version waiting, and where the row stands, in tags. A stopped row's state is
                    // the sentence below instead: a reason does not fit in a tag.
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        dev.wystore.ui.components.shortVersionName(item.versionName)?.let {
                            dev.wystore.ui.components.HubTag(it)
                        }
                        if (!failed) {
                            dev.wystore.ui.components.HubTag(
                                queueStatusLabel(item),
                                modifier = Modifier.weight(1f, fill = false),
                                tint = dev.wystore.ui.components.StatusTones.of(item.status, item.awaitingConfirmation).color()
                            )
                        }
                    }
                }
                // Beside the name, quiet, as on every other row. Not on a failed row, whose page is
                // a tap away and whose line below is about the failure.
                if (onWhatsNew != null && !failed) {
                    IconButton(onClick = onWhatsNew, modifier = Modifier.size(if (dev.wystore.ui.components.isNarrowScreen()) 32.dp else 40.dp)) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = stringResource(R.string.details_changelog),
                            modifier = Modifier.size(19.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
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
            if (failed) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    queueStatusLabel(item),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                // The reasons, reachable without pressing the button that answers the question.
                if (unconfirmed) {
                    IconButton(onClick = onConfirmUnverifiedSource) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = stringResource(R.string.unverified_source_why_open)
                        )
                    }
                }
            }
            item.progress?.takeIf { it.totalBytes > 0 }?.let { progress ->
                LinearProgressIndicator(
                    progress = { progress.fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(MaterialTheme.shapes.extraSmall),
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {}
                )
                if (item.status == InstallQueueStatus.DOWNLOADING) {
                    dev.wystore.ui.components.TransferProgressText(
                        progress,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            if (sourceProblem) {
                HubTextAction(
                    onClick = onSourceFailureHelp,
                    modifier = Modifier.align(Alignment.End)
                ) { Text(stringResource(R.string.source_failure_help)) }
            }
            if (item.awaitingConfirmation) {
                dev.wystore.ui.components.AwaitingConfirmationActions(
                    item = item,
                    onShowInstallDialog = onShowInstallDialog,
                    onCancelInstall = onCancelAwaitingInstall
                )
            } else Row(
                modifier = Modifier.fillMaxWidth(),
                // Grey pills for the way out, the accent for the step the row is waiting on - the
                // concept's pair, instead of a text button beside an outlined one beside a filled one.
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                when {
                    unconfirmed -> {
                        FilledTonalButton(onClick = onRetry) { Text(stringResource(R.string.common_retry)) }
                        Button(onClick = onConfirmUnverifiedSource) {
                            Text(stringResource(R.string.unverified_source_confirm))
                        }
                    }
                    needsReplace -> {
                        FilledTonalButton(onClick = onRetry) { Text(stringResource(R.string.common_retry)) }
                        Button(onClick = onReplace) { Text(stringResource(R.string.queue_replace)) }
                    }
                    stopped -> Button(onClick = onRetry) { Text(stringResource(R.string.common_retry)) }
                    // An item merely waiting in line can be started on its own, rather than only
                    // by starting the whole queue.
                    item.status == InstallQueueStatus.QUEUED -> {
                        FilledTonalButton(onClick = onSkip) { Text(stringResource(R.string.common_skip)) }
                        Button(onClick = onDownload) { Text(stringResource(R.string.queue_download_now)) }
                    }
                    // A transfer running now can be stopped without losing what it has fetched.
                    item.status == InstallQueueStatus.DOWNLOADING -> {
                        FilledTonalButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel)) }
                        // Nothing is asked of the user while it runs, so neither button is the accent.
                        FilledTonalButton(onClick = onPause) { Text(stringResource(R.string.common_pause)) }
                    }
                    item.status == InstallQueueStatus.PAUSED -> {
                        FilledTonalButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel)) }
                        Button(onClick = onResume) { Text(stringResource(R.string.common_resume)) }
                    }
                    else -> {
                        FilledTonalButton(onClick = onSkip) { Text(stringResource(R.string.common_skip)) }
                        FilledTonalButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel)) }
                    }
                }
            }
        }
    }
}

/** Enough to name the trouble without turning the card into a list. */
private const val PROBLEMS_SHOWN = 4
