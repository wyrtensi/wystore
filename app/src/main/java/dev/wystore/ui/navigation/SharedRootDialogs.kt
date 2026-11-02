package dev.wystore.ui.navigation

import dev.wystore.ui.components.DialogButton
import dev.wystore.ui.components.HubAlertDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.wystore.R
import dev.wystore.StoreUiState
import dev.wystore.StoreViewModel
import dev.wystore.ui.components.SourceFailureDialog
import dev.wystore.ui.components.UnverifiedSourceDialog
import dev.wystore.ui.components.WhatsNewDialog
import dev.wystore.updates.UserConfirmedInstaller

/**
 * The questions the store asks at the root, whichever screen raised them.
 *
 * Shared by the phone and the TV interface so both ask the same thing and act on the answer the
 * same way; only the screens around them differ.
 */
@Composable
fun SharedRootDialogs(
    viewModel: StoreViewModel,
    state: StoreUiState,
    sourceFailureHelp: Boolean,
    onSourceFailureHelpDismiss: () -> Unit
) {
    val context = LocalContext.current
    if (state.sourceRecoveryFailure != null && state.sourceReplacement == null) {
        HubAlertDialog(onDismissRequest = viewModel::cancelSourceRecovery,
            title = { Text(stringResource(R.string.dialog_replace_title)) },
            text = { Text(stringResource(R.string.source_recovery_body)) },
            confirmButton = {
                DialogButton(primary = true, enabled = !state.sourceRecoveryBusy, onClick = viewModel::retrySourceRecovery) {
                    Text(stringResource(R.string.common_retry))
                }
            },
            dismissButton = { DialogButton(onClick = viewModel::cancelSourceRecovery) { Text(stringResource(R.string.common_cancel)) } })
    }
    var removalQueueId by rememberSaveable { mutableStateOf<String?>(null) }
    val removalLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) {
        removalQueueId?.let(viewModel::sourceRemovalReturned)
        removalQueueId = null
    }
    state.sourceRemoval?.let { pending ->
        androidx.compose.runtime.LaunchedEffect(pending.queueId) {
            removalQueueId = requireNotNull(pending.queueId)
            viewModel.sourceRemovalLaunched()
            try {
                removalLauncher.launch(android.content.Intent(android.content.Intent.ACTION_DELETE,
                    android.net.Uri.parse("package:${pending.removedPackageName}"))
                    .putExtra(android.content.Intent.EXTRA_RETURN_RESULT, true))
            } catch (error: android.content.ActivityNotFoundException) {
                viewModel.sourceRemovalReturned(requireNotNull(pending.queueId))
                removalQueueId = null
                viewModel.reportSourceRemovalFailure()
            }
        }
    }
    state.sourceReplacement?.let { prompt ->
        val ready = prompt.staged != null
        HubAlertDialog(
            onDismissRequest = viewModel::dismissSourceReplacement,
            title = { Text(stringResource(if (ready) R.string.dialog_replace_title else R.string.source_verify_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                    if (prompt.preparing) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (ready) {
                        dev.wystore.ui.components.OwnershipTransferDetails(
                            installed = requireNotNull(prompt.consent).installed,
                            targetVersion = requireNotNull(prompt.staged).identity.versionName,
                            targetVersionCode = prompt.staged.identity.versionCode,
                            handover = dev.wystore.data.OwnershipHandover.REINSTALL)
                    } else Text(stringResource(R.string.source_verify_body))
                }
            },
            confirmButton = {
                DialogButton(primary = true, enabled = !prompt.preparing,
                    onClick = if (ready) viewModel::confirmSourceReplacement else viewModel::verifySourceReplacement) {
                    Text(stringResource(if (ready) R.string.dialog_replace_confirm else R.string.source_verify_title))
                }
            },
            dismissButton = {
                DialogButton(onClick = viewModel::dismissSourceReplacement) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (sourceFailureHelp) {
        SourceFailureDialog(
            status = state.selfUpdate,
            onCheckSelfUpdate = viewModel::checkSelfUpdate,
            onInstallSelfUpdate = {
                onSourceFailureHelpDismiss()
                viewModel.installSelfUpdate()
            },
            onDismiss = onSourceFailureHelpDismiss
        )
    }

    // Asked at the root so the answer is the same one from Home, Search, the app page, the library
    // and the queue - every screen a failed row can appear on.
    state.unverifiedSource?.let { prompt ->
        UnverifiedSourceDialog(
            appLabel = prompt.label,
            isUpdate = state.installed.any { it.packageName == prompt.packageName },
            onConfirm = viewModel::confirmUnverifiedSource,
            onDismiss = viewModel::dismissUnverifiedSource
        )
    }

    // Raised from the queue, the downloads and My apps, and the same window on every one of them.
    state.whatsNew?.let { prompt ->
        WhatsNewDialog(
            prompt = prompt,
            onOpenPage = viewModel::openWhatsNewPage,
            onDismiss = viewModel::dismissWhatsNew
        )
    }

    // A transfer the user asked for runs on whatever connection there is, so "Wi-Fi only" was
    // spent rather than honoured whenever someone pressed Install on mobile data. Asking is what
    // lets the button stay responsive and the setting stay true.
    if (state.meteredDownloadPrompt) {
        var always by rememberSaveable { mutableStateOf(false) }
        HubAlertDialog(
            onDismissRequest = viewModel::cancelMeteredDownload,
            title = { Text(stringResource(R.string.metered_download_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.metered_download_body))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { always = !always },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = always, onCheckedChange = { always = it })
                        Text(stringResource(R.string.metered_download_always))
                    }
                }
            },
            confirmButton = {
                DialogButton(primary = true, onClick = { viewModel.confirmMeteredDownload(always) }) {
                    Text(stringResource(R.string.metered_download_confirm))
                }
            },
            dismissButton = {
                DialogButton(onClick = viewModel::cancelMeteredDownload) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // The queue coordinator has always computed the next item after an install finishes; nothing
    // in the UI ever read it, so smart mode silently did nothing.
    val offeredNext by viewModel.offeredNext.collectAsState()
    offeredNext?.let { next ->
        HubAlertDialog(
            onDismissRequest = viewModel::queueDismissOfferedNext,
            title = { Text(stringResource(R.string.offer_next_title)) },
            text = { Text(stringResource(R.string.offer_next_body, next.label.ifBlank { next.packageName })) },
            confirmButton = {
                DialogButton(primary = true, onClick = {
                    val activity = context as? android.app.Activity
                    if (activity != null) {
                        viewModel.queueAcceptNext(UserConfirmedInstaller(activity))
                    } else {
                        viewModel.queueDismissOfferedNext()
                    }
                }) { Text(stringResource(R.string.offer_next_accept)) }
            },
            dismissButton = {
                DialogButton(onClick = viewModel::queueDismissOfferedNext) {
                    Text(stringResource(R.string.offer_next_dismiss))
                }
            }
        )
    }
}
