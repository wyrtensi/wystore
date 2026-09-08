package dev.wystore.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit

/**
 * Where the user's backup file lives, so it can be kept current without being asked again.
 *
 * Exporting was already possible; it just had to be repeated by hand, and a copy that is only as
 * good as the last time somebody remembered is not much of a copy. The reason it matters at all is
 * that Android keeps nothing for us: the installer of record - the record that says Wy Store looks
 * after an app - is cleared on every one of them the moment Wy Store itself is uninstalled,
 * verified on a device. Nothing about the library can be rebuilt by asking the system afterwards.
 *
 * The permission to write the file is taken persistably, so it survives restarts; if it is ever
 * revoked, or the file is deleted, the location is dropped rather than retried forever.
 */
class BackupLocation(context: Context) {

    private val appContext = context.applicationContext
    private val preferences =
        appContext.getSharedPreferences("wy_store_backup_location", Context.MODE_PRIVATE)

    val uri: Uri?
        get() = preferences.getString(KEY_URI, null)?.let(Uri::parse)

    fun remember(uri: Uri) {
        runCatching {
            appContext.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        preferences.edit { putString(KEY_URI, uri.toString()) }
    }

    fun forget() {
        preferences.edit { remove(KEY_URI) }
    }

    /**
     * Rewrites the remembered copy. Silent on both counts: nobody asked for it at this moment, and
     * a file that has gone away is not an error to report - it just stops being the copy.
     */
    fun refresh(json: String) {
        val target = uri ?: return
        val written = runCatching {
            // "wt" truncates: without it a shorter document leaves the tail of the previous one
            // behind and the file stops being valid JSON.
            appContext.contentResolver.openOutputStream(target, "wt")?.use { stream ->
                stream.write(json.toByteArray(Charsets.UTF_8))
            } ?: error("No output stream for $target")
        }
        if (written.isFailure) forget()
    }

    private companion object {
        const val KEY_URI = "backup_uri"
    }
}
