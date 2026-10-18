package dev.wystore.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.wystore.R
import dev.wystore.WhatsNewPrompt
import dev.wystore.ui.github.ReleaseNotesText

/**
 * The source's notes for the version it offers, over the list the user asked from.
 *
 * Long notes scroll inside the dialog, so its buttons stay on screen.
 */
@Composable
fun WhatsNewDialog(
    prompt: WhatsNewPrompt,
    onOpenPage: () -> Unit,
    onDismiss: () -> Unit
) {
    HubAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.details_changelog)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        prompt.label,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    val meta = listOfNotNull(
                        prompt.versionName?.takeIf { it.isNotBlank() }
                            ?.let { stringResource(R.string.whats_new_version, it) },
                        prompt.publishedAt?.takeIf { it.isNotBlank() }
                    ).joinToString(" · ")
                    if (meta.isNotEmpty()) {
                        Text(
                            meta,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                when {
                    prompt.loading -> Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) { WySpinner() }
                    prompt.error != null -> Text(
                        prompt.error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    prompt.notes == null -> Text(
                        stringResource(R.string.whats_new_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> Column(Modifier.verticalScroll(rememberScrollState())) {
                        if (prompt.markdown) {
                            ReleaseNotesText(
                                prompt.notes.take(MAX_NOTES),
                                style = MaterialTheme.typography.bodyMedium,
                                empty = stringResource(R.string.whats_new_empty)
                            )
                        } else {
                            Text(prompt.notes, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            DialogButton(onClick = onOpenPage) { Text(stringResource(R.string.library_open_page)) }
        },
        dismissButton = {
            DialogButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
        }
    )
}

/** The same cut the GitHub page makes: some projects paste whole changelogs into a release. */
private const val MAX_NOTES = 4000
