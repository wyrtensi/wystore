package dev.wystore.agent

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dev.wystore.data.PackageNameValidator
import dev.wystore.updates.model.QueueItemSnapshot

/**
 * The decisions behind [AgentProvider], kept free of Android so they can be tested.
 *
 * The provider is how an agent on a computer asks Wy Store, over adb, to fetch a list of apps that
 * the agent then installs with Wy Store as their installer - see docs/plans/2026-10-09-adb-agent-install.md.
 */
object AgentPolicy {
    const val AUTHORITY = "app.wystore.agent"
    const val MAX_PACKAGES = 50

    private const val SHELL_UID = 2000
    private const val ROOT_UID = 0

    /**
     * Only adb's shell, or root, may drive installs. The provider has to be exported for
     * `content call` to reach it, so the uid is the whole of the protection.
     */
    fun isAllowedCaller(uid: Int): Boolean = uid == SHELL_UID || uid == ROOT_UID

    data class ParsedPackages(
        val valid: List<String>,
        val invalid: List<String>,
        val overLimit: List<String>
    )

    fun parsePackages(argument: String?): ParsedPackages {
        val names = argument.orEmpty()
            .split(',', ' ', '\n', '\t', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        val (valid, invalid) = names.partition { runCatching { PackageNameValidator.requireValid(it) }.isSuccess }
        return ParsedPackages(valid.take(MAX_PACKAGES), invalid, valid.drop(MAX_PACKAGES))
    }

    /**
     * Whether what is on the device now is the file Wy Store verified for this row: at least its
     * version, signed by exactly the keys checked at download. Anything else is someone else's
     * install and must not be adopted as Wy Store's.
     */
    fun confirmsInstall(
        expectedVersionCode: Long,
        verifiedDigests: Set<String>,
        installedVersionCode: Long?,
        installedDigests: Set<String>
    ): Boolean =
        verifiedDigests.isNotEmpty() &&
            installedVersionCode != null &&
            installedVersionCode >= expectedVersionCode &&
            installedDigests == verifiedDigests

    data class RowStatus(val row: QueueItemSnapshot, val artifactSizes: List<Long>)

    fun artifactUri(queueId: String, index: Int): String = "content://$AUTHORITY/artifact/$queueId/$index"

    fun statusJson(rows: List<RowStatus>): String = JsonObject().apply {
        add("rows", JsonArray().apply {
            rows.forEach { (row, sizes) ->
                add(JsonObject().apply {
                    addProperty("id", row.id)
                    addProperty("package", row.packageName)
                    addProperty("label", row.label)
                    addProperty("state", row.state.name)
                    addProperty("versionName", row.versionName)
                    addProperty("versionCode", row.versionCode)
                    addProperty("downloadedBytes", row.downloadedBytes)
                    addProperty("totalBytes", row.totalBytes)
                    row.errorCode?.let { addProperty("error", it.name) }
                    row.errorDetail?.let { addProperty("detail", it) }
                    add("artifacts", JsonArray().apply {
                        sizes.forEachIndexed { index, size ->
                            add(JsonObject().apply {
                                addProperty("uri", artifactUri(row.id, index))
                                addProperty("size", size)
                            })
                        }
                    })
                })
            }
        })
    }.toString()
}
