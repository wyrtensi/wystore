package dev.wystore.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
                // Folded away rather than dropped: the four ordinary explanations are what make
                // this answerable at all, but they are four sentences on top of a decision.
                var reasonsShown by rememberSaveable { mutableStateOf(false) }
                TextButton(
                    onClick = { reasonsShown = !reasonsShown },
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.unverified_source_why_title))
                }
                if (reasonsShown) {
                    Text(
                        stringResource(R.string.unverified_source_why),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
