package dev.wystore.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.wystore.R

/**
 * The question behind the "Confirm" button: take a file the source would not vouch for, or not.
 *
 * RuStore publishes the fingerprint it expects an app to be signed with, and Wy Store refuses a
 * file signed with anything else. When the two disagree the store is contradicting itself, and
 * neither answer is obviously right - stale catalogue data looks exactly like a swapped file. So
 * the row asks rather than deciding, and the decision is spelled out here before it is taken: a
 * one-tap waiver of a signature check is not something to hand over on a button alone.
 *
 * What the answer does not waive is said out loud too, because it is the part that still protects
 * an update: Android refuses a file whose signature differs from the installed copy's, whatever
 * Wy Store was told.
 */
@Composable
fun UnverifiedSourceDialog(
    appLabel: String,
    isUpdate: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.unverified_source_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.unverified_source_body, appLabel))
                Text(
                    stringResource(
                        if (isUpdate) R.string.unverified_source_body_update
                        else R.string.unverified_source_body_new
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.unverified_source_accept))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}
