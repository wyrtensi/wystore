package dev.wystore.data

/**
 * The vendor shell as a report line: which one, and the MIUI switch that decides how installs work.
 *
 * Only ever used to describe the device in a report. How the store installs is decided by what the
 * system actually does - see dev.wystore.updates.SessionInstallRejection - never by these values.
 */
object FirmwareShell {

    fun describe(properties: (String) -> String): String {
        val miui = properties("ro.miui.ui.version.name").trim()
        val hyperOs = properties("ro.mi.os.version.name").trim()
        if (miui.isEmpty() && hyperOs.isEmpty()) return "нет признаков MIUI"
        val name = when {
            hyperOs.isNotEmpty() -> "HyperOS $hyperOs"
            else -> "MIUI $miui"
        }
        // MIUI reads the switch with a default of "on" unless the build is a CTS one, so an empty
        // value is not the same as "off". An app may also be denied the read, which looks the same.
        val optimization = when (properties("persist.sys.miui_optimization").trim()) {
            "true" -> "вкл"
            "false" -> "выкл"
            else -> if (properties("ro.miui.cts").trim() == "1") {
                "не задана (по умолчанию выкл)"
            } else {
                "не задана (по умолчанию вкл)"
            }
        }
        return "$name, оптимизация MIUI: $optimization"
    }

    /** Reads a build property through `getprop`, the public way; empty when it is unset or unreadable. */
    fun systemProperty(name: String): String = runCatching {
        val process = ProcessBuilder("getprop", name).redirectErrorStream(true).start()
        val value = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
        value.trim()
    }.getOrDefault("")
}
