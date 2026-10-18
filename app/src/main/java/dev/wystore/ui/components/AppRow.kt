package dev.wystore.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.wystore.R
import dev.wystore.localization.StatusTextResolver

/**
 * One app in a list.
 *
 * The action sits on the same line as the name, the way a store row reads, instead of on a row of
 * its own underneath: the card used to be twice as tall as its content and every list looked like
 * a stack of forms. Progress and the secondary action only take space when they exist.
 */
@Composable
fun AppRow(
    state: PackageUiState,
    onRowClick: () -> Unit,
    onPrimaryActionClick: () -> Unit,
    onSecondaryActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    /**
     * The source every row of the list comes from, when the list is one source's. Its badge then
     * says nothing the section title has not: "Popular on RuStore" put "RuStore" on every row.
     */
    implicitSource: String? = null,
    /** Shows the source's notes for the version it offers; the row only offers it when given. */
    onWhatsNew: (() -> Unit)? = null
) {
    WyCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "${state.label}, ${state.versionName.orEmpty()}, ${state.status.code}"
            },
        onClick = onRowClick
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcon(
                    model = state.iconUrl,
                    contentDescription = null,
                    fallbackPackageName = state.packageName
                )
                Spacer(Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    // Two lines: catalogue names run long ("VK Видео: кино, сериалы, ТВ...") and one
                    // line cut every one of them after the first word or two.
                    Text(
                        text = state.label,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    val version = shortVersionName(state.versionName)
                    val shownSource = state.sourceProvenance
                        ?.takeUnless { implicitSource != null && it.equals(implicitSource, ignoreCase = true) }
                    if (version != null || shownSource != null) Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // The catalogue does not state a version for every app, and a lone dash
                        // in front of the badge says nothing worth a line.
                        version?.let { version ->
                            Text(
                                text = version,
                                // A long version string used to claim the whole line and squeeze
                                // the source badge down to nothing: the badge is a word, the
                                // version is the part that can be cut.
                                modifier = Modifier.weight(1f, fill = false),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        shownSource?.let { source ->
                                // The reducer carries the raw enum name; the card shows the name the
                                // source actually goes by.
                                SourceLabel(text = sourceDisplayName(source))
                            }
                    }
                    // A catalogue row says what a store listing says - rating and section. The
                    // package name stays for rows the catalogue knows nothing about.
                    val listing = listingLine(state.rating, state.category)
                    Text(
                        text = listing.ifEmpty { state.packageName },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (state.primaryAction != PrimaryAction.None) {
                    Spacer(Modifier.width(10.dp))
                    PrimaryActionButton(
                        action = state.primaryAction,
                        onClick = onPrimaryActionClick
                    )
                }
            }

            // What is happening to this app, in words. The reducer has always produced this and
            // the resolver has always been able to render it; nothing ever displayed it.
            if (state.status.code in REPORTED_STATES) {
                Text(
                    text = StatusTextResolver.resolve(LocalContext.current, state.status),
                    style = MaterialTheme.typography.labelMedium,
                    color = when (state.status.code) {
                        StatusCode.FAILED_NETWORK,
                        StatusCode.FAILED_STORAGE,
                        StatusCode.FAILED_SIGNATURE,
                        StatusCode.FAILED_SOURCE_UNCONFIRMED,
                        StatusCode.FAILED_GENERIC -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Same for a row: until the download reports a size there is no fraction to draw,
            // and the row showed a percentage that never moved.
            if (state.progress != null ||
                state.transferInfo != null ||
                state.status.code == StatusCode.DOWNLOADING
            ) {
                TransferProgress(
                    progress = state.progress,
                    transferInfo = state.transferInfo
                )
            }

            val secondaryClick = onSecondaryActionClick?.takeIf {
                state.secondaryAction != null && state.secondaryAction != SecondaryAction.None
            }
            if (secondaryClick != null || onWhatsNew != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    // Quiet, like the secondary action beside it: the row's own button is the step to
                    // take, this only answers what the version on offer brings.
                    onWhatsNew?.let { showNotes ->
                        TextButton(
                            onClick = showNotes,
                            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 40.dp)
                        ) { Text(stringResource(R.string.details_changelog)) }
                    }
                    if (secondaryClick != null) TextButton(
                        onClick = secondaryClick,
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 40.dp)
                    ) {
                        Text(
                            when (state.secondaryAction) {
                                SecondaryAction.Cancel -> stringResource(R.string.common_cancel)
                                SecondaryAction.Retry -> stringResource(R.string.common_retry)
                                SecondaryAction.CheckUpdate -> stringResource(R.string.common_check)
                                SecondaryAction.None, null -> ""
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * What a store listing says under the name: rating and section, "★ 4,7 · Финансы". Empty when the
 * catalogue states neither. Shared by every list that shows a catalogue app, so Home and Search
 * stop describing the same app in two different ways.
 */
@Composable
fun listingLine(rating: Double?, categorySlug: String?): String {
    val category = categorySlug?.let { slug ->
        CategoryLabels.stringRes(slug)?.let { stringResource(it) } ?: CategoryLabels.humanize(slug)
    }
    return listOfNotNull(
        rating?.let { "★ " + String.format(java.util.Locale.getDefault(), "%.1f", it) },
        category
    ).joinToString(" · ")
}

/**
 * The one action a row offers. Filled for the step the user is expected to take, tonal for the
 * ones that merely acknowledge a state the app is already in.
 *
 * Install is outlined: a catalogue list is a column of apps that are not installed, and a filled
 * button on every one of them made ten identical bright buttons in a row, pushed the names onto
 * two lines and left nothing to tell the update waiting for the user from an offer.
 */
@Composable
private fun PrimaryActionButton(action: PrimaryAction, onClick: () -> Unit) {
    val contentPadding = RowButtonPadding
    val sizing = Modifier.defaultMinSize(minWidth = 76.dp, minHeight = 40.dp)
    when (action) {
        PrimaryAction.Install -> OutlinedButton(onClick, sizing, contentPadding = contentPadding) {
            Text(stringResource(R.string.common_install), maxLines = 1)
        }
        PrimaryAction.Update -> Button(onClick, sizing, contentPadding = contentPadding) {
            Text(stringResource(R.string.common_update), maxLines = 1)
        }
        PrimaryAction.Resume -> Button(onClick, sizing, contentPadding = contentPadding) {
            Text(stringResource(R.string.common_resume), maxLines = 1)
        }
        PrimaryAction.Retry -> Button(onClick, sizing, contentPadding = contentPadding) {
            Text(stringResource(R.string.common_retry), maxLines = 1)
        }
        PrimaryAction.ConfirmSource -> Button(onClick, sizing, contentPadding = contentPadding) {
            Text(stringResource(R.string.unverified_source_confirm), maxLines = 1)
        }
        PrimaryAction.DownloadNow -> Button(onClick, sizing, contentPadding = contentPadding) {
            Text(stringResource(R.string.queue_download_now), maxLines = 1)
        }
        PrimaryAction.Open -> FilledTonalButton(onClick, sizing, contentPadding = contentPadding) {
            Text(stringResource(R.string.common_open), maxLines = 1)
        }
        PrimaryAction.Pause -> FilledTonalButton(onClick, sizing, contentPadding = contentPadding) {
            Text(stringResource(R.string.common_pause), maxLines = 1)
        }
        PrimaryAction.Installing -> FilledTonalButton(
            onClick = {},
            modifier = sizing,
            enabled = false,
            contentPadding = contentPadding
        ) {
            WySpinner(size = 14.dp, strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.common_installing), maxLines = 1)
        }
        PrimaryAction.None -> Unit
    }
}

/** Narrower than the default, so a long name keeps its room beside the button. */
val RowButtonPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 8.dp)

/**
 * States worth a line of their own. "Installed" and the idle states say nothing the row does not
 * already show through its action button.
 */
private val REPORTED_STATES = setOf(
    StatusCode.CHECKING,
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
    StatusCode.FAILED_SOURCE_UNCONFIRMED,
    StatusCode.FAILED_GENERIC,
    StatusCode.CANCELLED
)
