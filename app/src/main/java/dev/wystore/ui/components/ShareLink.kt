package dev.wystore.ui.components

import android.content.Context
import android.content.Intent

/**
 * Hands a link to whatever the user shares with.
 *
 * A chooser is always created rather than relying on the system's default target, so sharing never
 * silently goes somewhere the user did not pick. A device with nothing that accepts text is
 * possible, and is not worth crashing over.
 */
fun shareLink(context: Context, subject: String, url: String, chooserTitle: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, url)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, chooserTitle)) }
}
