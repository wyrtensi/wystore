package dev.wystore.root

import dev.wystore.data.VerifiedInstallPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Why a root operation did not work, where the reason is Wy Store's own rather than the shell's.
 *
 * The raw [RootResult.output] is whatever `pm` printed and is technical by nature; these are the
 * cases the app decides for itself, and they need to be renderable in the user's language.
 */
enum class RootFailure {
    NO_ARTIFACTS,
    ARTIFACT_MISSING,
    SESSION_NOT_CREATED,
    WRITE_FAILED,
    INVALID_PACKAGE_NAME,
    TIMED_OUT,
    ROOT_UNAVAILABLE
}

data class RootResult(
    val success: Boolean,
    val output: String,
    val failure: RootFailure? = null
)

class RootInstaller {
    suspend fun isAvailable(): Boolean = execute("id").success

    suspend fun install(plan: VerifiedInstallPlan, update: Boolean): RootResult = withContext(Dispatchers.IO) {
        val files = plan.files
        if (files.isEmpty()) {
            return@withContext RootResult(false, "No APK files in the plan", RootFailure.NO_ARTIFACTS)
        }
        if (files.any { !it.isFile || it.length() <= 0L }) {
            return@withContext RootResult(false, "A verified APK is no longer on disk", RootFailure.ARTIFACT_MISSING)
        }
        val create = execute("pm install-create --user 0 ${if (update) "-r" else ""}".trim())
        val session = Regex("\\[(\\d+)]").find(create.output)?.groupValues?.getOrNull(1)
            ?: return@withContext RootResult(false, create.output, RootFailure.SESSION_NOT_CREATED)
        var committed = false
        try {
            for ((index, file) in files.withIndex()) {
                val split = if (index == 0) "base.apk" else "split_$index.apk"
                val command = "pm install-write -S ${file.length()} $session $split ${shellQuote(file.absolutePath)}"
                val write = execute(command)
                if (!write.success) {
                    return@withContext RootResult(false, write.output, RootFailure.WRITE_FAILED)
                }
            }
            val commit = execute("pm install-commit $session")
            committed = commit.success && commit.output.contains("Success", true)
            RootResult(committed, commit.output)
        } finally {
            if (!committed) execute("pm install-abandon $session")
        }
    }

    suspend fun uninstall(packageName: String): RootResult = withContext(Dispatchers.IO) {
        if (!packageName.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))) {
            return@withContext RootResult(false, "Rejected package name: $packageName", RootFailure.INVALID_PACKAGE_NAME)
        }
        val result = execute("pm uninstall --user 0 ${shellQuote(packageName)}")
        result.copy(success = result.success && result.output.contains("Success", ignoreCase = true))
    }

    private suspend fun execute(command: String): RootResult = withContext(Dispatchers.IO) {
        runCatching {
            val process = ProcessBuilder("/system/bin/su", "-c", command).redirectErrorStream(true).start()
            val finished = process.waitFor(90, TimeUnit.SECONDS)
            if (!finished) {
                process.destroy()
                if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly()
                return@runCatching RootResult(false, "su timed out after 90s", RootFailure.TIMED_OUT)
            }
            val output = process.inputStream.bufferedReader().use { it.readText() }.take(2000)
            val exitVal = process.exitValue()
            android.util.Log.d("WyStoreRoot", "su exited with value $exitVal, output: $output")
            RootResult(exitVal == 0, output)
        }.getOrElse {
            android.util.Log.e("WyStoreRoot", "Root execution error", it)
            RootResult(false, it.message.orEmpty(), RootFailure.ROOT_UNAVAILABLE)
        }
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
