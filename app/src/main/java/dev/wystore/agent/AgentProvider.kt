package dev.wystore.agent

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.ParcelFileDescriptor
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dev.wystore.background.TransferDispatcher
import dev.wystore.data.ManagedSource
import dev.wystore.data.local.WyStoreDatabase
import dev.wystore.data.local.toSnapshot
import dev.wystore.updates.QueueCoordinator
import dev.wystore.updates.QueueRepository
import dev.wystore.updates.model.QueueState
import kotlinx.coroutines.runBlocking
import java.io.FileNotFoundException

/**
 * Lets an agent on a computer install a list of apps through Wy Store over adb, without root.
 *
 *     adb shell content call --uri content://app.wystore.agent --method install --arg "ru.one,ru.two"
 *     adb shell content call --uri content://app.wystore.agent --method status
 *     adb shell "content read --uri content://app.wystore.agent/artifact/<id>/0 | pm install-write -S <size> <session> 0.apk -"
 *     adb shell content call --uri content://app.wystore.agent --method installed --arg <id>
 *
 * Every answer is a Bundle holding one `json` string. Only the shell and root may call it; see
 * [AgentPolicy.isAllowedCaller]. The design is in docs/plans/2026-10-09-adb-agent-install.md and
 * tools/wystore_adb_install.py drives the whole round.
 */
class AgentProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        requireAllowedCaller()
        val context = requireNotNull(context).applicationContext
        val json = runBlocking {
            when (method) {
                METHOD_INSTALL -> install(context, arg)
                METHOD_STATUS -> status(context, arg)
                METHOD_INSTALLED -> installed(context, arg)
                METHOD_CANCEL -> cancel(context, arg)
                else -> error("unknown method $method")
            }
        }
        return Bundle().apply { putString(KEY_JSON, json) }
    }

    private suspend fun install(context: android.content.Context, arg: String?): String {
        val parsed = AgentPolicy.parsePackages(arg)
        val repository = QueueRepository.getInstance(context)
        val dao = WyStoreDatabase.getInstance(context).updateQueueDao
        val handoff = AdbHandoff(context)
        val queued = JsonArray()
        for (packageName in parsed.valid) {
            val created = repository.enqueueManualInstall(packageName, packageName, ManagedSource.RUSTORE)
            // Nothing created means the queue is already working on this package; that row is
            // what the agent gets, rather than a refusal it would have to work around.
            val row = created ?: dao.getByPackage(packageName)
                .filter { it.source == ManagedSource.RUSTORE.name && it.state !in FINISHED }
                .maxByOrNull { it.updatedAt }
            if (row == null) {
                queued.add(JsonObject().apply {
                    addProperty("package", packageName)
                    addProperty("error", "not queued")
                })
                continue
            }
            handoff.mark(row.id)
            if (created != null) TransferDispatcher.dispatch(context, row.id)
            queued.add(JsonObject().apply {
                addProperty("package", packageName)
                addProperty("id", row.id)
                addProperty("state", row.state)
            })
        }
        return JsonObject().apply {
            add("queued", queued)
            add("invalid", JsonArray().apply { parsed.invalid.forEach(::add) })
            add("overLimit", JsonArray().apply { parsed.overLimit.forEach(::add) })
        }.toString()
    }

    private suspend fun status(context: android.content.Context, arg: String?): String {
        val repository = QueueRepository.getInstance(context)
        val handoff = AdbHandoff(context)
        val wanted = arg?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: handoff.ids()
        val rows = wanted.mapNotNull { id ->
            val entity = repository.getEntityById(id)
            if (entity == null) {
                // The row was removed in the app; nothing will ever come of the mark.
                handoff.unmark(id)
                return@mapNotNull null
            }
            val sizes = if (entity.state == QueueState.READY_TO_INSTALL.name) {
                repository.artifactFilesFor(id).map { it.length() }
            } else {
                emptyList()
            }
            AgentPolicy.RowStatus(entity.toSnapshot(), sizes)
        }
        return AgentPolicy.statusJson(rows)
    }

    private suspend fun installed(context: android.content.Context, arg: String?): String {
        val id = arg?.trim().orEmpty()
        val result = AdbHandoff(context).confirm(id)
        return JsonObject().apply {
            addProperty("id", id)
            when (result) {
                AdbHandoff.Confirmation.Confirmed -> addProperty("confirmed", true)
                is AdbHandoff.Confirmation.Refused -> {
                    addProperty("confirmed", false)
                    addProperty("reason", result.reason)
                }
            }
        }.toString()
    }

    private suspend fun cancel(context: android.content.Context, arg: String?): String {
        val id = arg?.trim().orEmpty()
        runCatching { QueueCoordinator(context).cancel(id) }
        AdbHandoff(context).unmark(id)
        return JsonObject().apply { addProperty("id", id); addProperty("canceled", true) }.toString()
    }

    /** `content://app.wystore.agent/artifact/<queueId>/<index>`, read-only, verified copies only. */
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        requireAllowedCaller()
        if (mode != "r") throw FileNotFoundException("read-only")
        val segments = uri.pathSegments
        if (segments.size != 3 || segments[0] != "artifact") throw FileNotFoundException("unknown uri")
        val id = segments[1]
        val index = segments[2].toIntOrNull() ?: throw FileNotFoundException("bad index")
        val context = requireNotNull(context).applicationContext
        if (!AdbHandoff(context).isHandedOff(id)) throw FileNotFoundException("not handed off")
        val file = runBlocking {
            val repository = QueueRepository.getInstance(context)
            val entity = repository.getEntityById(id)
            if (entity?.state != QueueState.READY_TO_INSTALL.name) null
            else repository.artifactFilesFor(id).getOrNull(index)
        } ?: throw FileNotFoundException("no artifact $index for $id")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun requireAllowedCaller() {
        if (!AgentPolicy.isAllowedCaller(Binder.getCallingUid())) {
            throw SecurityException("Only adb shell may use the Wy Store agent interface")
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = if (uri.pathSegments.firstOrNull() == "artifact") "application/vnd.android.package-archive" else null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private companion object {
        const val METHOD_INSTALL = "install"
        const val METHOD_STATUS = "status"
        const val METHOD_INSTALLED = "installed"
        const val METHOD_CANCEL = "cancel"
        const val KEY_JSON = "json"
        val FINISHED = setOf(QueueState.INSTALLED.name, QueueState.CANCELED.name, QueueState.SKIPPED.name, QueueState.OFFER_NEXT.name)
    }
}
