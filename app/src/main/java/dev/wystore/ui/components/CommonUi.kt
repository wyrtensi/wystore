package dev.wystore.ui.components

import android.icu.text.CompactDecimalFormat
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.wystore.InstallQueueItem
import dev.wystore.InstallQueueStatus
import dev.wystore.updates.UnverifiedSourceConsent
import dev.wystore.updates.model.QueueErrorCode
import dev.wystore.R
import dev.wystore.data.InstallSource
import dev.wystore.data.VerificationError
import dev.wystore.localization.VerificationTextResolver
import dev.wystore.data.ManagedApp
import dev.wystore.data.DownloadProgress
import dev.wystore.data.ManagedSource
import dev.wystore.data.StoreApp
import dev.wystore.data.StoreReview
import java.text.DecimalFormat
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** A rating, in the warm accent so it reads as a highlight next to the neutral category pills. */
@Composable
fun RatingPill(rating: Double, count: Int?) {
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = MaterialTheme.colorScheme.onTertiaryContainer
        )
        Text(
            text = DecimalFormat("0.0").format(rating) + (count?.let { " · " + formatCount(it) }.orEmpty()),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            maxLines = 1
        )
    }
}

/**
 * One category tag under an app.
 *
 * [category] is the source's own slug, which is what used to be printed here; [CategoryLabels]
 * turns it into the name the catalogue itself uses.
 */
@Composable
fun CategoryPill(category: String) {
    // A faint tag, as every secondary fact is now; the outlined box read as a button.
    val text = CategoryLabels.stringRes(category)?.let { stringResource(it) } ?: CategoryLabels.humanize(category)
    HubTag(text, tint = sourceTint(text))
}

@Composable
fun ReviewCard(review: StoreReview) {
    WyCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    review.author.ifBlank { stringResource(R.string.review_anonymous_author) },
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                review.rating?.let { RatingPill(it.toDouble(), null) }
            }
            val date = review.publishedAt?.substringBefore("T")
            if (date != null || review.likes > 0 || review.dislikes > 0) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (date != null) {
                        Text(
                            if (review.edited) stringResource(R.string.review_edited, date) else date,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Other readers' verdict on the review, which is what separates a complaint
                    // everyone recognised from one nobody did.
                    if (review.likes > 0 || review.dislikes > 0) {
                        Text(
                            stringResource(R.string.review_votes, review.likes, review.dislikes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Text(review.text, style = MaterialTheme.typography.bodyMedium, maxLines = 5, overflow = TextOverflow.Ellipsis)
            // The developer's answer, which the page publishes and this card used to throw away -
            // so the worst reviews looked unanswered when they had been answered.
            review.developerResponse?.let { response ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Column(
                        Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            stringResource(R.string.review_developer_reply),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            response,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailFacts(app: StoreApp) {
    val noData = stringResource(R.string.common_no_data)
    val notSpecified = stringResource(R.string.common_not_specified)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // The first section is in the page's panel already; only the others are added here.
        val more = app.categories.drop(1)
        if (more.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                more.forEach { CategoryPill(it) }
            }
        }
        // Tiles of label over value, two to a row, as the concept lays out its facts. The rating
        // is left out: it stands in the panel at the top of the page, and here it was the same
        // number twice.
        val facts = listOfNotNull(
            stringResource(R.string.details_label_version) to "${app.versionName} (${app.versionCode})",
            stringResource(R.string.details_label_updated) to (formatStoreDate(app.updatedAt) ?: noData),
            stringResource(R.string.details_label_size) to formatSize(app.sizeBytes),
            stringResource(R.string.details_label_min_android) to
                stringResource(R.string.details_min_android_value, app.minAndroidVersion ?: notSpecified),
            app.downloadsText?.let { stringResource(R.string.details_label_downloads) to formatDownloads(it) }
        )
        FactTiles(facts)
        // The fingerprint is a check rather than a fact about the app, and too long for a tile.
        WyCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.details_label_fingerprint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    app.signatureHint ?: notSpecified,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                )
                Text(
                    stringResource(R.string.details_fingerprint_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Facts as tiles, two to a row: label over value, the concept's way of laying them out. */
@Composable
fun FactTiles(facts: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        facts.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { (label, value) -> FactTile(label, value, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** One fact as the concept's tile: the label small and grey, the value under it in weight. */
@Composable
private fun FactTile(label: String, value: String, modifier: Modifier = Modifier) {
    WyCard(modifier = modifier) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun CompactSettingSwitch(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().switchRow(checked, enabled, onChange), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/**
 * Makes a row with a switch one control: a tap on the label toggles it, and the remote's focus lights
 * the whole row. On the switch alone the focus was a halo around its thumb that a TV across the room
 * did not show. The switch inside is given `onCheckedChange = null`, so it is not a second stop.
 */
@Composable
fun Modifier.switchRow(checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit): Modifier =
    clip(MaterialTheme.shapes.small).toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)

@Composable
fun Loading(operation: String? = null) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        WySpinner(size = 40.dp, strokeWidth = 4.dp)
        operation?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
fun OperationProgress(
    item: InstallQueueItem,
    onConfirmUnverifiedSource: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
    /** For an install waiting on Android's dialog; see [AwaitingConfirmationActions]. */
    onShowInstallDialog: ((String) -> Unit)? = null,
    onCancelInstall: ((String) -> Unit)? = null
) {
    WyCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = if (item.status == InstallQueueStatus.FAILED) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(queueStatusLabel(item), style = MaterialTheme.typography.titleSmall)
            val progress = item.progress
            if (item.status == InstallQueueStatus.FAILED) {
                // The line above already names the failure in the user's language. This used to
                // repeat the raw detail underneath it - an exception message, in English.
                if (UnverifiedSourceConsent.isAnswerable(item.errorCode, item.detail)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        onRetry?.let {
                            HubTextAction(onClick = it) { Text(stringResource(R.string.common_retry)) }
                            Spacer(Modifier.width(4.dp))
                        }
                        onConfirmUnverifiedSource?.let {
                            Button(onClick = it) {
                                Text(stringResource(R.string.unverified_source_confirm))
                            }
                        }
                    }
                }
            } else if (item.awaitingConfirmation && onCancelInstall != null) {
                AwaitingConfirmationActions(
                    item = item,
                    onShowInstallDialog = { onShowInstallDialog?.invoke(item.id) },
                    onCancelInstall = { onCancelInstall(item.id) }
                )
            } else if (item.status == InstallQueueStatus.COMPLETE) {
                Text(item.detail ?: stringResource(R.string.queue_status_install_complete), style = MaterialTheme.typography.bodySmall)
            } else if (progress != null && progress.totalBytes > 0) {
                LinearProgressIndicator(
                    progress = { progress.fraction.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraSmall)
                )
                TransferProgressText(progress)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WySpinner(size = 20.dp, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.progress_dont_close), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/**
 * The way out of an install waiting on Android's dialog that is no longer on screen.
 *
 * A stray tap beside the dialog can close it without Android saying so, and the row then read
 * "Installing" with nothing to press for a minute. Cancel puts the row back to Install with its
 * files; the dialog itself can be shown again while this run of the app still has it.
 */
@Composable
fun AwaitingConfirmationActions(
    item: InstallQueueItem,
    onShowInstallDialog: () -> Unit,
    onCancelInstall: () -> Unit
) {
    Text(
        stringResource(
            if (item.canShowDialogAgain) R.string.queue_awaiting_confirmation_hint
            else R.string.queue_awaiting_confirmation_cancel_hint
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
    ) {
        androidx.compose.material3.FilledTonalButton(onClick = onCancelInstall) { Text(stringResource(R.string.common_cancel)) }
        if (item.canShowDialogAgain) {
            Button(onClick = onShowInstallDialog) { Text(stringResource(R.string.queue_show_install_dialog)) }
        }
    }
}

/**
 * How far a download has got, in the same words wherever it is shown: size, share, speed and time
 * left. The queue card on Updates showed a bare bar, and the speed read "calculating" everywhere.
 */
@Composable
fun TransferProgressText(progress: DownloadProgress, modifier: Modifier = Modifier) {
    val etaSuffix = progress.etaSeconds
        ?.let { " · " + stringResource(R.string.progress_eta, formatDuration(it)) }
        .orEmpty()
    val head = stringResource(
        R.string.progress_transfer_full,
        formatSize(progress.downloadedBytes),
        formatSize(progress.totalBytes),
        progress.artifactIndex,
        progress.artifactCount,
        (progress.fraction * 100).toInt()
    )
    Text(
        "$head\n${formatSpeed(progress.bytesPerSecond)}$etaSuffix",
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
fun queueStatusLabel(item: InstallQueueItem): String = when (item.status) {
    // The detail is whatever the data layer last recorded on the row, so a leftover exception
    // message stood in for "preparing". A row that is preparing says so.
    InstallQueueStatus.RESOLVING -> stringResource(R.string.queue_status_preparing)
    InstallQueueStatus.QUEUED -> stringResource(R.string.queue_status_queued)
    InstallQueueStatus.DOWNLOADING -> stringResource(R.string.queue_status_downloading)
    InstallQueueStatus.PAUSED -> stringResource(R.string.queue_status_paused)
    InstallQueueStatus.VERIFYING -> stringResource(R.string.queue_status_verifying)
    InstallQueueStatus.READY -> stringResource(R.string.queue_status_ready)
    InstallQueueStatus.INSTALLING ->
        if (item.awaitingConfirmation) stringResource(R.string.queue_status_awaiting_confirmation)
        else stringResource(R.string.queue_status_installing)
    InstallQueueStatus.COMPLETE -> stringResource(R.string.queue_status_complete)
    InstallQueueStatus.CANCELED -> stringResource(R.string.queue_status_canceled)
    // Failure text comes from the typed code, because the raw detail is an exception message from
    // the data layer and is not translated. A verification failure is the exception: it records
    // which of the ten checks refused the file, and the ten mean very different things.
    // The one failure that is a question: it is asked in the same words wherever the row shows up,
    // and the buttons beside it are the answer.
    InstallQueueStatus.FAILED ->
        if (UnverifiedSourceConsent.isAnswerable(item.errorCode, item.detail)) {
            stringResource(R.string.unverified_source_status)
        } else {
            verificationLabel(item.detail)
                ?: item.errorCode?.let { queueErrorLabel(it) }
                ?: stringResource(R.string.queue_status_failed)
        }
}

/**
 * The sentence for a rejected APK, when the row carries one.
 *
 * Every verification failure used to arrive as "the APK signature did not pass" - a downgrade, a
 * package mismatch, a missing base APK, all of them. An update whose version is simply already
 * installed was announced as if the file had been tampered with, which is both wrong and the most
 * alarming thing the app can say. The reason was recorded all along and only ever thrown away.
 */
@Composable
fun verificationLabel(detail: String?): String? {
    val error = detail?.let { name ->
        VerificationError.entries.firstOrNull { it.name == name }
    } ?: return null
    return stringResource(VerificationTextResolver.stringRes(error))
}

@Composable
fun queueErrorLabel(code: QueueErrorCode): String =
    stringResource(dev.wystore.updates.QueueErrorStrings.stringRes(code))

@Composable
fun sourceLabel(source: InstallSource): String = when (source) {
    InstallSource.GOOGLE_PLAY -> "Google Play"
    InstallSource.WY_STORE -> "Wy Store"
    InstallSource.OTHER -> stringResource(R.string.source_other)
}

/**
 * The same source, in the width a badge on a list row actually has.
 *
 * On a compact screen the row has room for a version and a short word, not for "Google Play" - the
 * badge came out as "Goog..." and named nothing. The full name is still what the app page uses.
 */
@Composable
fun managedSourceLabel(app: ManagedApp): String = when (app.source) {
    ManagedSource.RUSTORE -> "RuStore"
    ManagedSource.GITHUB -> "GitHub"
    null -> stringResource(R.string.source_not_saved)
}

/**
 * Formats a byte count. [res] may be null only where Android resources are unavailable
 * (plain JVM unit tests); in that case a locale-independent fallback is produced.
 */
fun formatSize(res: Resources?, bytes: Long): String = when {
    bytes <= 0 -> res?.getString(R.string.common_not_specified) ?: "n/a"
    bytes < 1024 * 1024 -> res?.getString(R.string.common_size_kb, bytes / 1024) ?: "${bytes / 1024} KB"
    else -> {
        val value = DecimalFormat("0.0").format(bytes / 1024.0 / 1024.0)
        res?.getString(R.string.common_size_mb, value) ?: "$value MB"
    }
}

@Composable
fun formatSize(bytes: Long): String = formatSize(LocalResources.current, bytes)

/**
 * RuStore's download count in the interface language - "12M+" rather than its own "12 млн +". Text
 * it sends in some other shape is shown as sent.
 */
fun formatDownloads(text: String, locale: java.util.Locale): String =
    dev.wystore.data.RuStoreDownloads.atLeast(text)
        ?.let { CompactDecimalFormat.getInstance(locale, CompactDecimalFormat.CompactStyle.SHORT).format(it) + "+" }
        ?: text

@Composable
fun formatDownloads(text: String): String = formatDownloads(text, LocalConfiguration.current.locales[0])

/**
 * How many people rated an app, short: "4,4 млн". Shown as the bare number it read "4439591",
 * which nobody can take in at a glance.
 */
@Composable
fun formatCount(count: Int): String =
    CompactDecimalFormat.getInstance(LocalConfiguration.current.locales[0], CompactDecimalFormat.CompactStyle.SHORT)
        .format(count)

/** The same count in full, with the locale's digit grouping: "4 439 591". */
@Composable
fun formatCountInFull(count: Int): String =
    java.text.NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0]).format(count)

fun formatSpeed(res: Resources, bytesPerSecond: Long): String = when {
    bytesPerSecond <= 0 -> res.getString(R.string.common_speed_calculating)
    bytesPerSecond < 1024 * 1024 -> res.getString(R.string.common_speed_kb, bytesPerSecond / 1024)
    else -> res.getString(R.string.common_speed_mb, DecimalFormat("0.0").format(bytesPerSecond / 1024.0 / 1024.0))
}

@Composable
fun formatSpeed(bytesPerSecond: Long): String = formatSpeed(LocalResources.current, bytesPerSecond)

fun formatDuration(res: Resources, seconds: Long): String = when {
    seconds < 60 -> res.getString(R.string.common_duration_seconds, seconds)
    seconds < 3_600 -> res.getString(R.string.common_duration_minutes, seconds / 60)
    else -> res.getString(R.string.common_duration_hours, seconds / 3_600, seconds % 3_600 / 60)
}

@Composable
fun formatDuration(seconds: Long): String = formatDuration(LocalResources.current, seconds)

/**
 * Renders a source timestamp as a date in the reader's locale.
 *
 * RuStore hands back a full ISO-8601 instant; printing it verbatim put
 * "2026-08-26T14:58:02.517+00:00" on the app page where a date belongs.
 */
fun formatStoreDate(raw: String?): String? {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return null
    val date = runCatching { OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate() }
        .recoverCatching { LocalDate.parse(value.substringBefore("T")) }
        .getOrNull()
        ?: return value
    return date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
}
