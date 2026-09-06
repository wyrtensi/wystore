package dev.wystore.ui.components

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.wystore.InstallQueueItem
import dev.wystore.InstallQueueStatus
import dev.wystore.updates.model.QueueErrorCode
import dev.wystore.R
import dev.wystore.data.InstallSource
import dev.wystore.data.ManagedApp
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
            text = DecimalFormat("0.0").format(rating) + (count?.let { " · $it" }.orEmpty()),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            maxLines = 1
        )
    }
}

@Composable
fun CategoryPill(category: String) {
    Text(
        text = category,
        modifier = Modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
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
            review.publishedAt?.substringBefore("T")?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(review.text, style = MaterialTheme.typography.bodyMedium, maxLines = 5, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailFacts(app: StoreApp) {
    val noData = stringResource(R.string.common_no_data)
    val notSpecified = stringResource(R.string.common_not_specified)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (app.categories.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                app.categories.forEach { CategoryPill(it) }
            }
        }
        // A two-column table rather than a paragraph of "Label: value" sentences, so the values
        // line up and the eye can run down them.
        WyCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FactRow(
                    stringResource(R.string.details_label_version),
                    "${app.versionName} (${app.versionCode})"
                )
                FactRow(stringResource(R.string.details_label_updated), formatStoreDate(app.updatedAt) ?: noData)
                FactRow(stringResource(R.string.details_label_size), formatSize(app.sizeBytes))
                FactRow(
                    stringResource(R.string.details_label_min_android),
                    stringResource(R.string.details_min_android_value, app.minAndroidVersion ?: notSpecified)
                )
                app.rating?.let {
                    FactRow(
                        stringResource(R.string.details_label_rating),
                        stringResource(R.string.details_rating_value, DecimalFormat("0.0").format(it), app.ratingCount ?: 0)
                    )
                }
                app.downloadsText?.let { FactRow(stringResource(R.string.details_label_downloads), it) }
                WyDivider()
                FactRow(
                    stringResource(R.string.details_label_fingerprint),
                    app.signatureHint ?: notSpecified
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

@Composable
fun CompactSettingSwitch(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
fun ruStoreTaskLabel(status: String): String = stringResource(
    when (status) {
        "QUEUED" -> R.string.rustore_task_queued
        "PREPARING" -> R.string.rustore_task_preparing
        "DOWNLOADING" -> R.string.rustore_task_downloading
        "VERIFYING" -> R.string.rustore_task_verifying
        "PROBING" -> R.string.rustore_task_probing
        "COMPLETE" -> R.string.rustore_task_complete
        "FAILED" -> R.string.rustore_task_failed
        else -> R.string.rustore_task_verifying
    }
)

@Composable
fun Loading(operation: String? = null) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CircularProgressIndicator()
        operation?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
fun OperationProgress(item: InstallQueueItem) {
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
                Text(item.detail ?: stringResource(R.string.queue_status_failed), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
            } else if (item.status == InstallQueueStatus.COMPLETE) {
                Text(item.detail ?: stringResource(R.string.queue_status_install_complete), style = MaterialTheme.typography.bodySmall)
            } else if (progress != null && progress.totalBytes > 0) {
                LinearProgressIndicator(
                    progress = { progress.fraction.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraSmall)
                )
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
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.progress_dont_close), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun queueStatusLabel(item: InstallQueueItem): String = when (item.status) {
    InstallQueueStatus.RESOLVING -> item.detail ?: stringResource(R.string.queue_status_preparing)
    InstallQueueStatus.QUEUED -> stringResource(R.string.queue_status_queued)
    InstallQueueStatus.DOWNLOADING -> stringResource(R.string.queue_status_downloading)
    InstallQueueStatus.VERIFYING -> stringResource(R.string.queue_status_verifying)
    InstallQueueStatus.READY -> stringResource(R.string.queue_status_ready)
    InstallQueueStatus.INSTALLING -> stringResource(R.string.queue_status_installing)
    InstallQueueStatus.COMPLETE -> stringResource(R.string.queue_status_complete)
    InstallQueueStatus.CANCELED -> stringResource(R.string.queue_status_canceled)
    // Failure text comes from the typed code, because the raw detail is an exception message from
    // the data layer and is not translated.
    InstallQueueStatus.FAILED -> item.errorCode?.let { queueErrorLabel(it) }
        ?: item.detail
        ?: stringResource(R.string.queue_status_failed)
}

@Composable
fun queueErrorLabel(code: QueueErrorCode): String = when (code) {
    QueueErrorCode.NETWORK -> stringResource(R.string.queue_error_network)
    QueueErrorCode.RATE_LIMITED -> stringResource(R.string.queue_error_rate_limited)
    QueueErrorCode.SOURCE_CHANGED -> stringResource(R.string.queue_error_source_changed)
    QueueErrorCode.STORAGE_FULL -> stringResource(R.string.queue_error_storage_full)
    QueueErrorCode.INTEGRITY -> stringResource(R.string.queue_error_integrity)
    QueueErrorCode.SIGNATURE -> stringResource(R.string.queue_error_signature)
    QueueErrorCode.INCOMPATIBLE -> stringResource(R.string.queue_error_incompatible)
    QueueErrorCode.PERMISSION -> stringResource(R.string.queue_error_permission)
    QueueErrorCode.INSTALL_CANCELED -> stringResource(R.string.queue_error_install_canceled)
    QueueErrorCode.INSTALL_FAILED -> stringResource(R.string.queue_error_install_failed)
    QueueErrorCode.ARTIFACT_MISSING -> stringResource(R.string.queue_error_artifact_missing)
    QueueErrorCode.TIMEOUT -> stringResource(R.string.queue_error_timeout)
}

@Composable
fun sourceLabel(source: InstallSource): String = when (source) {
    InstallSource.GOOGLE_PLAY -> "Google Play"
    InstallSource.WY_STORE -> "Wy Store"
    InstallSource.OTHER -> stringResource(R.string.source_other)
}

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
