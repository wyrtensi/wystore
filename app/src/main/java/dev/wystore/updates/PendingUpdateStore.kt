package dev.wystore.updates

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dev.wystore.data.PendingUpdate
import java.io.File

class PendingUpdateStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("wy_store", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val rootDirectory = File(appContext.filesDir, "pending_updates")

    fun isMigrated(): Boolean = preferences.getBoolean(KEY_MIGRATED, false)

    fun markMigrated() {
        preferences.edit()
            .putBoolean(KEY_MIGRATED, true)
            .remove(KEY_PENDING_UPDATES)
            .apply()
    }

    fun readLegacy(): List<PendingUpdate> {
        val json = preferences.getString(KEY_PENDING_UPDATES, "[]") ?: "[]"
        val items = runCatching {
            gson.fromJson<List<PendingUpdate>>(json, object : TypeToken<List<PendingUpdate>>() {}.type)
        }.getOrDefault(emptyList())
        return items.filter(::hasCompleteFiles)
    }

    private fun hasCompleteFiles(update: PendingUpdate): Boolean =
        update.filePaths.isNotEmpty() && update.filePaths.all { path ->
            val file = File(path)
            file.isFile && file.length() > 0L && runCatching {
                file.canonicalPath.startsWith(rootDirectory.canonicalPath + File.separator)
            }.getOrDefault(false)
        }

    companion object {
        private const val KEY_PENDING_UPDATES = "pending_updates"
        private const val KEY_MIGRATED = "pending_updates_migrated_to_room"
    }
}
