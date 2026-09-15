package dev.wystore.ui.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import dev.wystore.R
import dev.wystore.data.StoreApp
import dev.wystore.localization.StatusTextResolver
import dev.wystore.ui.components.AppIcon
import dev.wystore.ui.components.PackageUiState
import dev.wystore.ui.components.PrimaryAction
import dev.wystore.ui.components.RowAction
import dev.wystore.ui.components.RowActionPolicy
import dev.wystore.ui.components.StatusCode
import androidx.compose.material3.MaterialTheme as M3Theme

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
 * The focus look every TV control shares: outlined in the primary colour, over the shape the
 * phone uses for the same element. Material's phone highlight is a faint tint nobody sees from a
 * sofa; this is the one signal on the screen that says where the remote is.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun tvFocusBorder(shape: androidx.compose.ui.graphics.Shape = M3Theme.shapes.large): Border = Border(
    border = BorderStroke(3.dp, MaterialTheme.colorScheme.border),
    inset = 0.dp,
    shape = shape
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun tvFocusGlow(): Glow = Glow(
    elevationColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
    elevation = 12.dp
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSectionTitle(text: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier.padding(bottom = 8.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** The phone's source badge: a small tonal label that says where an app comes from. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvBadge(
    text: String,
    modifier: Modifier = Modifier,
    container: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.secondaryContainer,
    content: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSecondaryContainer
) {
    Text(
        text = text,
        color = content,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(M3Theme.shapes.extraSmall)
            .background(container)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

/**
 * One app in a row: icon, name, and either what is happening to it or where it comes from.
 *
 * The same parts as the phone's app row, stacked for a rail instead of laid out for a list, in the
 * same container tone on the same page, so the two interfaces read as one app.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvAppCard(
    app: StoreApp,
    state: PackageUiState?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Marks an app from the phone catalogue shown among TV apps, so the two are not confused. */
    forPhone: Boolean = false
) {
    val context = LocalContext.current
    val status = state?.status?.takeIf { it.code in CARD_STATES }
        ?.let { StatusTextResolver.resolve(context, it) }
    val shape = M3Theme.shapes.large
    Card(
        onClick = onClick,
        modifier = modifier
            .width(TvCardWidth)
            .tvPointerClick(onClick = onClick)
            .semantics { contentDescription = listOfNotNull(app.name, status).joinToString(", ") },
        shape = CardDefaults.shape(shape),
        scale = CardDefaults.scale(focusedScale = 1.06f),
        border = CardDefaults.border(focusedBorder = tvFocusBorder(shape)),
        glow = CardDefaults.glow(focusedGlow = tvFocusGlow()),
        colors = CardDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            if (status != null) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else if (forPhone) {
                TvBadge(
                    stringResource(R.string.tv_badge_phone),
                    container = MaterialTheme.colorScheme.tertiary,
                    content = MaterialTheme.colorScheme.onTertiary
                )
            } else {
                TvBadge(app.categories.firstOrNull() ?: stringResource(R.string.source_rustore))
            }
        }
    }
}

val TvCardWidth = 208.dp

/** Height a row of [TvAppCard]s needs so a grown, focused card is not clipped. */
val TvRowHeight = 244.dp

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

/**
 * Home's first card, as on the phone: is anything waiting. Primary-toned when there is, quiet when
 * there is not, and the same shape either way so the screen does not jump. It opens My apps.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvUpdateHero(
    readyCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasUpdates = readyCount > 0
    val container = if (hasUpdates) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val onContainer = if (hasUpdates) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val shape = M3Theme.shapes.large
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().tvPointerClick(onClick = onClick),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        border = ClickableSurfaceDefaults.border(focusedBorder = tvFocusBorder(shape)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = container,
            contentColor = onContainer,
            focusedContainerColor = container,
            focusedContentColor = onContainer
        )
    ) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(M3Theme.shapes.medium)
                    .background(onContainer.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (hasUpdates) Icons.Outlined.Refresh else Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                    tint = onContainer
                )
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(if (hasUpdates) R.string.home_updates_available else R.string.home_all_up_to_date),
                    style = MaterialTheme.typography.titleMedium,
                    color = onContainer
                )
                Text(
                    if (hasUpdates) stringResource(R.string.home_ready_to_install_count, readyCount)
                    else stringResource(R.string.home_background_checks),
                    style = MaterialTheme.typography.bodyMedium,
                    color = onContainer.copy(alpha = 0.75f)
                )
            }
        }
    }
}

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

/** The filled button for the step the user is expected to take. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: Painter? = null
) {
    val shape = M3Theme.shapes.extraLarge
    Button(
        onClick = onClick,
        modifier = modifier.tvPointerClick(enabled, onClick),
        enabled = enabled,
        shape = ButtonDefaults.shape(shape),
        colors = ButtonDefaults.colors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            focusedContentColor = MaterialTheme.colorScheme.onPrimary
        ),
        border = ButtonDefaults.border(focusedBorder = tvFocusBorder(shape).copy(
            border = BorderStroke(3.dp, MaterialTheme.colorScheme.onBackground)
        )),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp)
    ) {
        ButtonContent(text, icon)
    }
}

/** The tonal button for everything beside the main step, as on the phone. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: Painter? = null
) {
    val shape = M3Theme.shapes.extraLarge
    Button(
        onClick = onClick,
        modifier = modifier.tvPointerClick(enabled, onClick),
        enabled = enabled,
        shape = ButtonDefaults.shape(shape),
        colors = ButtonDefaults.colors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            focusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            focusedContentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        border = ButtonDefaults.border(focusedBorder = tvFocusBorder(shape)),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
    ) {
        ButtonContent(text, icon)
    }
}

/**
 * A round button that is only an icon, for an action every TV user recognises by its picture -
 * the microphone. [label] is still read out by TalkBack.
 *
 * A clickable surface rather than a Button: a Button lays its content out in a row with its own
 * minimum size and padding, which pushed the icon off the centre of the circle.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvIconButton(
    icon: Painter,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = androidx.compose.foundation.shape.CircleShape
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(56.dp)
            .tvPointerClick(onClick = onClick)
            .semantics { contentDescription = label },
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        border = ClickableSurfaceDefaults.border(focusedBorder = tvFocusBorder(shape)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            focusedContentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun ButtonContent(text: String, icon: Painter?) {
    if (icon != null) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
    }
    Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
}

@Composable
fun rememberIconPainter(vector: ImageVector): Painter = rememberVectorPainter(vector)

/** What a screen shows instead of an empty list, laid out like the phone's empty state. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvEmptyState(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = M3Theme.shapes.large,
        colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.width(24.dp))
                TvSecondaryButton(actionLabel, onAction)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvMessageBanner(text: String, modifier: Modifier = Modifier) {
    Box(modifier.padding(32.dp)) {
        Surface(
            shape = M3Theme.shapes.large,
            colors = SurfaceDefaults.colors(
                containerColor = M3Theme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Text(text, modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun TvVerticalGap() = Spacer(Modifier.height(28.dp))
