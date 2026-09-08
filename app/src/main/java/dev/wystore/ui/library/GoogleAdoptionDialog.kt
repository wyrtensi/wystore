package dev.wystore.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.wystore.GoogleAdoptionPrompt
import dev.wystore.R
import dev.wystore.data.AdoptionCandidate
import dev.wystore.ui.components.WySpinner

/**
 * Asks which app to put in place of one that came from Google.
 *
 * This dialog ends in an uninstall, so it says that first and shows what would replace the app
 * rather than deciding for the user. When the store carries the very same package there is nothing
 * to choose between - that one is shown alone and marked as the same package. Otherwise the nearest
 * few by name are offered, the first one larger, because a list of equals invites a coin toss.
 */
@Composable
fun GoogleAdoptionDialog(
    prompt: GoogleAdoptionPrompt,
    onPick: (AdoptionCandidate) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.adopt_google_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.adopt_google_text, prompt.app.label),
                    style = MaterialTheme.typography.bodyMedium
                )
                when {
                    prompt.loading -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        WySpinner(size = 18.dp, strokeWidth = 2.dp)
                        Text(
                            stringResource(R.string.adopt_google_searching),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    prompt.candidates.isEmpty() -> Text(
                        stringResource(R.string.adopt_google_not_found),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )

                    else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(
                                if (prompt.candidates.first().exact) R.string.adopt_google_exact
                                else R.string.adopt_google_pick
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        prompt.candidates.forEachIndexed { index, candidate ->
                            CandidateRow(
                                candidate = candidate,
                                leading = index == 0,
                                onPick = { onPick(candidate) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

@Composable
private fun CandidateRow(
    candidate: AdoptionCandidate,
    leading: Boolean,
    onPick: () -> Unit
) {
    val iconSize = if (leading) 48.dp else 32.dp
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onPick),
        shape = MaterialTheme.shapes.medium,
        color = if (leading) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AsyncImage(
                model = candidate.iconUrl,
                contentDescription = null,
                modifier = Modifier.size(iconSize).clip(MaterialTheme.shapes.small)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    candidate.label,
                    style = if (leading) {
                        MaterialTheme.typography.titleMedium
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    candidate.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                stringResource(R.string.adopt_google_confirm),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
