package dev.wystore.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One thing that went wrong, kept so a report can say what happened rather than "it broke". */
data class DiagnosticsEvent(
    val at: Long,
    val packageName: String,
    val code: String,
    val detail: String?
)

/** Everything a bug report should carry, gathered in one place so the text is not assembled twice. */
data class Diagnostics(
    val appVersionName: String,
    val appVersionCode: Long,
    val installerOfSelf: String?,
    val androidRelease: String,
    val sdkInt: Int,
    val securityPatch: String?,
    val manufacturer: String,
    val model: String,
    val device: String,
    val abis: List<String>,
    val screenWidthPx: Int,
    val screenHeightPx: Int,
    val densityDpi: Int,
    val smallestWidthDp: Int,
    val fontScale: Float,
    val rootAvailable: Boolean?,
    val rootSilentInstall: Boolean,
    val rootBackgroundDownloads: Boolean,
    val notificationsGranted: Boolean,
    val canInstallUnknownApps: Boolean,
    val batteryOptimizationsIgnored: Boolean,
    val managedApps: Int,
    val githubRepositories: Int,
    val queueRows: List<String>,
    val lastCheck: String?,
    val settings: List<Pair<String, String>>,
    val events: List<DiagnosticsEvent>
)

/**
 * Renders a report a person can paste into an issue.
 *
 * Plain text on purpose: it is read by whoever receives it, not parsed. It carries what actually
 * decides behaviour here - the Android version and the device, whether root is there, whether the
 * three permissions that gate installing are granted, and what the queue last did - and it carries
 * no identifiers of the person: no account, no file paths, no URLs, and package names only for the
 * apps this store manages, which is what a queue problem is about.
 */
object DiagnosticsReport {

    fun render(diagnostics: Diagnostics, now: Long): String = buildString {
        appendLine("Wy Store ${diagnostics.appVersionName} (${diagnostics.appVersionCode})")
        appendLine("Отчёт собран: ${timestamp(now)}")
        diagnostics.installerOfSelf?.let { appendLine("Установлен через: $it") }
        appendLine()

        appendLine("Android ${diagnostics.androidRelease} (SDK ${diagnostics.sdkInt})")
        diagnostics.securityPatch?.let { appendLine("Патч безопасности: $it") }
        appendLine("Устройство: ${diagnostics.manufacturer} ${diagnostics.model} (${diagnostics.device})")
        appendLine("ABI: ${diagnostics.abis.joinToString(", ").ifBlank { "—" }}")
        appendLine(
            "Экран: ${diagnostics.screenWidthPx}×${diagnostics.screenHeightPx}, " +
                "${diagnostics.densityDpi} dpi, sw${diagnostics.smallestWidthDp}dp, " +
                "шрифт ×${diagnostics.fontScale}"
        )
        appendLine()

        appendLine(
            "Root: " + when (diagnostics.rootAvailable) {
                true -> "доступен"
                false -> "нет"
                null -> "не проверялся"
            }
        )
        appendLine("Тихая root-установка: ${onOff(diagnostics.rootSilentInstall)}")
        appendLine("Фоновые загрузки через root: ${onOff(diagnostics.rootBackgroundDownloads)}")
        appendLine()

        appendLine("Уведомления: ${yesNo(diagnostics.notificationsGranted)}")
        appendLine("Установка из неизвестных источников: ${yesNo(diagnostics.canInstallUnknownApps)}")
        appendLine("Без ограничений батареи: ${yesNo(diagnostics.batteryOptimizationsIgnored)}")
        appendLine()

        appendLine("Принятых приложений: ${diagnostics.managedApps}")
        appendLine("Репозиториев GitHub: ${diagnostics.githubRepositories}")
        appendLine("Последняя проверка: ${diagnostics.lastCheck ?: "не было"}")
        appendLine()

        appendLine("Очередь (${diagnostics.queueRows.size}):")
        if (diagnostics.queueRows.isEmpty()) {
            appendLine("  пусто")
        } else {
            diagnostics.queueRows.forEach { appendLine("  $it") }
        }
        appendLine()

        appendLine("Последние сбои (${diagnostics.events.size}):")
        if (diagnostics.events.isEmpty()) {
            appendLine("  не записано")
        } else {
            diagnostics.events.forEach { event ->
                appendLine(
                    "  ${timestamp(event.at)} ${event.packageName} ${event.code}" +
                        (event.detail?.let { ": $it" } ?: "")
                )
            }
        }
        appendLine()

        appendLine("Настройки:")
        diagnostics.settings.forEach { (key, value) -> appendLine("  $key = $value") }
    }

    private fun onOff(value: Boolean) = if (value) "вкл" else "выкл"

    private fun yesNo(value: Boolean) = if (value) "да" else "нет"

    private fun timestamp(at: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(at))
}
