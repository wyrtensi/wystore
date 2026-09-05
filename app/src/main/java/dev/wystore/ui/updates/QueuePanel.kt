package dev.wystore.ui.updates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.wystore.InstallQueueItem
import dev.wystore.InstallQueueStatus
import dev.wystore.R
import dev.wystore.ui.components.StateBadge
import dev.wystore.ui.components.TransferProgress
import dev.wystore.ui.components.WyCard
import dev.wystore.ui.components.formatSize

@Composable
fun QueuePanel(
    activeItem: InstallQueueItem?,
    queuedCount: Int,
    onCancelActive: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (activeItem == null && queuedCount == 0) return

    WyCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.queue_panel_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (queuedCount > 0) {
                    StateBadge(text = stringResource(R.string.queue_panel_queued_count, queuedCount))
                }
            }

            activeItem?.let { item ->
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.titleSmall
                )

                val progress = item.progress
                val transferInfo = progress?.let {
                    stringResource(
                        R.string.queue_panel_transfer,
                        formatSize(it.downloadedBytes),
                        formatSize(it.totalBytes),
                        (it.fraction * 100).toInt()
                    )
                }

                TransferProgress(
                    progress = progress?.fraction,
                    transferInfo = transferInfo ?: item.detail
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onCancelActive) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            }
        }
    }
}
