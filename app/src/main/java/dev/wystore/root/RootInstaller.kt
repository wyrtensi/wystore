package dev.wystore.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dev.wystore.data.VerifiedInstallPlan
import java.io.File
import java.util.concurrent.TimeUnit

data class RootResult(val success: Boolean, val output: String)

class RootInstaller {
    suspend fun isAvailable(): Boolean = execute("id").success

    suspend fun install(plan: VerifiedInstallPlan, update: Boolean): RootResult = withContext(Dispatchers.IO) {
        val files = plan.files
        if (files.isEmpty()) return@withContext RootResult(false, "Нет APK для установки")
        if (files.any { !it.isFile || it.length() <= 0L }) return@withContext RootResult(false, "Проверенный APK больше недоступен")
        val create = execute("pm install-create --user 0 ${if (update) "-r" else ""}".trim())
        val session = Regex("\\[(\\d+)]").find(create.output)?.groupValues?.getOrNull(1)
            ?: return@withContext RootResult(false, "Не удалось создать сессию: ${create.output}")
        var committed = false
        try {
            for ((index, file) in files.withIndex()) {
                val split = if (index == 0) "base.apk" else "split_$index.apk"
                val command = "pm install-write -S ${file.length()} $session $split ${shellQuote(file.absolutePath)}"
                val write = execute(command)
                if (!write.success) return@withContext RootResult(false, "Не удалось записать APK: ${write.output}")
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
            return@withContext RootResult(false, "Некорректное имя пакета")
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
                return@runCatching RootResult(false, "Root-команда превысила лимит времени")
            }
            val output = process.inputStream.bufferedReader().use { it.readText() }.take(2000)
            val exitVal = process.exitValue()
            android.util.Log.d("WyStoreRoot", "su exited with value $exitVal, output: $output")
            RootResult(exitVal == 0, output)
        }.getOrElse { 
            android.util.Log.e("WyStoreRoot", "Root execution error", it)
            RootResult(false, it.message ?: "Root недоступен") 
        }
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
