package dev.wystore.ui.components

import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import dev.wystore.R

/**
 * Removes an app, through Android's own dialog.
 *
 * Uninstalling used to live only in the library, behind a confirmation of ours, and went through
 * the root installer - so on a phone without root it answered "root denied" and did nothing. The
 * platform's dialog needs no root and asks the question better than we can; this is the page the
 * user is already on when they decide.
 *
 * Needs REQUEST_DELETE_PACKAGES in the manifest. Without it Android drops the intent without a
 * word, and the button looks broken rather than forbidden.
 */
@Composable
fun UninstallIconButton(packageName: String) {
    val context = LocalContext.current
    IconButton(
        onClick = {
            val removal = Intent(Intent.ACTION_DELETE, "package:$packageName".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(removal) }
        }
    ) {
        Icon(
            imageVector = Icons.Outlined.Delete,
            contentDescription = stringResource(R.string.details_uninstall),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
