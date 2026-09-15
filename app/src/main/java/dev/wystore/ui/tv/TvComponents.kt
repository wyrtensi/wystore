package dev.wystore.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import dev.wystore.R
import dev.wystore.data.StoreApp
import dev.wystore.localization.StatusTextResolver
import dev.wystore.ui.components.AppIcon
import dev.wystore.ui.components.PackageUiState
import dev.wystore.ui.components.PrimaryAction
import dev.wystore.ui.components.RowAction
import dev.wystore.ui.components.RowActionPolicy
import dev.wystore.ui.components.StatusCode

/**
 * Lets a pointer press what a remote presses.
 *
 * The TV components answer the remote's OK and nothing else, so a mouse, the air mouse many boxes
 * ship with, or a touch in the emulator clicked on a card and nothing happened.
 */
fun Modifier.tvPointerClick(enabled: Boolean = true, onClick: () -> Unit): Modifier =
    if (!enabled) this else pointerInput(onClick) { detectTapGestures(onTap = { onClick() }) }

/** What a TV screen can do with a package; the same handlers the phone rows call. */
data class TvPackageActions(
    val onEnqueue: (String) -> Unit,
    val onInstallDownloaded: (String) -> Unit,
    val onDownloadNow: (String) -> Unit,
    val onPause: (String) -> Unit,
    val onResume: (String) -> Unit,
    val onConfirmSource: (String) -> Unit,
    val onLaunch: (String) -> Unit
) {
    fun perform(state: PackageUiState) {
        val packageName = state.packageName
        when (RowActionPolicy.actionFor(state.primaryAction, state.status.code)) {
            RowAction.Enqueue -> onEnqueue(packageName)
            RowAction.InstallDownloaded -> onInstallDownloaded(packageName)
            RowAction.DownloadNow -> onDownloadNow(packageName)
            RowAction.Pause -> onPause(packageName)
            RowAction.Resume -> onResume(packageName)
            RowAction.ConfirmSource -> onConfirmSource(packageName)
            RowAction.OpenApp -> onLaunch(packageName)
            RowAction.Nothing -> Unit
        }
    }
}

/**
 * The focus look every TV control shares: grown, outlined in the primary colour. Material's own
 * phone highlight is a faint tint that cannot be seen from a sofa.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun tvFocusBorder(): Border = Border(
    border = BorderStroke(3.dp, MaterialTheme.colorScheme.border),
    shape = RoundedCornerShape(16.dp)
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(bottom = 12.dp)
    )
}

/** One app in a row or a grid: icon, name and, when there is one, what is happening to it. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvAppCard(
    app: StoreApp,
    state: PackageUiState?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val status = state?.status?.takeIf { it.code in CARD_STATES }
        ?.let { StatusTextResolver.resolve(context, it) }
    Card(
        onClick = onClick,
        modifier = modifier
            .width(TvCardWidth)
            .tvPointerClick(onClick = onClick)
            .semantics { contentDescription = listOfNotNull(app.name, status).joinToString(", ") },
        shape = CardDefaults.shape(RoundedCornerShape(16.dp)),
        scale = CardDefaults.scale(focusedScale = 1.08f),
        border = CardDefaults.border(focusedBorder = tvFocusBorder()),
        colors = CardDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AppIcon(
                model = app.iconUrl,
                contentDescription = null,
                size = 64.dp,
                fallbackPackageName = app.packageName
            )
            Text(
                text = app.name.ifBlank { app.packageName },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = status ?: app.categories.firstOrNull().orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = if (status != null) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

val TvCardWidth = 196.dp

private val CARD_STATES = setOf(
    StatusCode.QUEUED,
    StatusCode.DOWNLOADING,
    StatusCode.PAUSED,
    StatusCode.VERIFYING,
    StatusCode.READY_TO_INSTALL,
    StatusCode.AWAITING_UNKNOWN_SOURCES_PERMISSION,
    StatusCode.AWAITING_USER_CONFIRMATION,
    StatusCode.INSTALLING,
    StatusCode.INSTALLED,
    StatusCode.FAILED_NETWORK,
    StatusCode.FAILED_STORAGE,
    StatusCode.FAILED_SIGNATURE,
    StatusCode.FAILED_SOURCE_UNCONFIRMED,
    StatusCode.FAILED_GENERIC
)

/** The label of the one button that moves a package forward, or null when there is none. */
@Composable
fun tvPrimaryLabel(action: PrimaryAction): String? = when (action) {
    PrimaryAction.Install -> stringResource(R.string.common_install)
    PrimaryAction.Update -> stringResource(R.string.common_update)
    PrimaryAction.Resume -> stringResource(R.string.common_resume)
    PrimaryAction.Retry -> stringResource(R.string.common_retry)
    PrimaryAction.ConfirmSource -> stringResource(R.string.unverified_source_confirm)
    PrimaryAction.DownloadNow -> stringResource(R.string.queue_download_now)
    PrimaryAction.Open -> stringResource(R.string.common_open)
    PrimaryAction.Pause -> stringResource(R.string.common_pause)
    PrimaryAction.Installing -> stringResource(R.string.common_installing)
    PrimaryAction.None -> null
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier.tvPointerClick(enabled, onClick),
        enabled = enabled,
        border = ButtonDefaults.border(focusedBorder = tvFocusBorder()),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp)
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, maxLines = 1)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.tvPointerClick(enabled, onClick),
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, maxLines = 1)
    }
}

/** A line that says the screen has nothing, and why, with the way out when there is one. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvEmptyState(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(modifier.fillMaxWidth().padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.width(24.dp))
            TvSecondaryButton(actionLabel, onAction)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvMessageBanner(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(24.dp)
    ) {
        androidx.tv.material3.Surface(
            shape = RoundedCornerShape(12.dp),
            colors = androidx.tv.material3.SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface
            )
        ) {
            Text(text, modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** Height a row of [TvAppCard]s needs so a grown, focused card is not clipped. */
val TvRowHeight = 230.dp

@Composable
fun TvVerticalGap() = Spacer(Modifier.height(32.dp))

@Composable
fun TvIconSpacer() = Spacer(Modifier.size(16.dp))
