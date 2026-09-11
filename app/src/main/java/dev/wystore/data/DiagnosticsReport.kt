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

/**
 * One queue row: what the store thinks of it, and underneath, what everything else thinks.
 *
 * Two levels because they can disagree, and the disagreement is the interesting part. A row that
 * says AVAILABLE while the system says its task has already failed is a stall the store cannot see
 * from the inside, and that is exactly the shape of failure the first report of this kind could
 * not describe.
 */
data class DiagnosticsQueueRow(
    val headline: String,
    val details: List<String>
)

/** Everything a bug report should carry, gathered in one place so the text is not assembled twice. */
data class Diagnostics(
    val appVersionName: String,
    val appVersionCode: Long,
    val applicationId: String,
    val targetSdk: Int,
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
    val locale: String,
    val rootAvailable: Boolean?,
    val rootSilentInstall: Boolean,
    val rootBackgroundDownloads: Boolean,
    val networkSummary: String,
    val dataSaver: String,
    val transferMechanism: String,
    val cacheFreeBytes: Long,
    val notificationsGranted: Boolean,
    val notificationChannels: List<Pair<String, String>>,
    val canInstallUnknownApps: Boolean,
    val batteryOptimizationsIgnored: Boolean,
    val powerSaveMode: Boolean,
    val deviceIdleMode: Boolean,
    val standbyBucket: String,
    val managedApps: Int,
    val githubRepositories: Int,
    val queueRows: List<DiagnosticsQueueRow>,
    val backgroundWork: List<Pair<String, String>>,
    val lastCheck: String?,
    val settings: List<Pair<String, String>>,
    val events: List<DiagnosticsEvent>
)

/**
 * Renders a report a person can paste into an issue.
 *
 * Plain text on purpose: it is read by whoever receives it, not parsed. It carries what actually
 * decides behaviour here - the Android version and the device, whether root is there, whether the
 * permissions and the power rules that gate downloading and installing are in the way, and what
 * the queue last did - and it carries no identifiers of the person: no account, no file paths, no
 * URLs, and package names only for the apps this store manages, which is what a queue problem is
 * about.
 *
 * It also carries the verdicts that belong to the system rather than to the store: WorkManager
 * state for every transfer and every check, whether the network counts as metered, whether the app
 * sits in a standby bucket that stops its jobs, and whether the notification channels a foreground
 * transfer needs are still switched on. The first report of a queue that never moved said only
 * that two rows were waiting: the failure had happened inside WorkManager, before any of this app
 * ran, so the store had nothing to log and the report read as if nothing were wrong at all. What
 * the store cannot see about itself is now asked of whoever can see it.
 */
object DiagnosticsReport {

    fun render(diagnostics: Diagnostics, now: Long): String = buildString {
        appendLine("Wy Store ${diagnostics.appVersionName} (${diagnostics.appVersionCode})")
        appendLine("Отчёт собран: ${timestamp(now)}")
        appendLine("Пакет: ${diagnostics.applicationId}, targetSdk ${diagnostics.targetSdk}")
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
        appendLine("Язык: ${diagnostics.locale}")
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

        appendLine("Сеть: ${diagnostics.networkSummary}")
        appendLine("Экономия трафика: ${diagnostics.dataSaver}")
        appendLine("Механизм передачи: ${diagnostics.transferMechanism}")
        appendLine("Свободно под загрузки: ${megabytes(diagnostics.cacheFreeBytes)}")
        appendLine()

        appendLine("Уведомления: ${yesNo(diagnostics.notificationsGranted)}")
        diagnostics.notificationChannels.forEach { (name, state) -> appendLine("  $name: $state") }
        appendLine("Установка из неизвестных источников: ${yesNo(diagnostics.canInstallUnknownApps)}")
        appendLine("Без ограничений батареи: ${yesNo(diagnostics.batteryOptimizationsIgnored)}")
        appendLine("Энергосбережение: ${onOff(diagnostics.powerSaveMode)}")
        appendLine("Спящий режим (Doze): ${yesNo(diagnostics.deviceIdleMode)}")
        appendLine("Категория активности: ${diagnostics.standbyBucket}")
        appendLine()

        appendLine("Принятых приложений: ${diagnostics.managedApps}")
        appendLine("Репозиториев GitHub: ${diagnostics.githubRepositories}")
        appendLine("Последняя проверка: ${diagnostics.lastCheck ?: "не было"}")
        appendLine()

        appendLine("Очередь (${diagnostics.queueRows.size}):")
        if (diagnostics.queueRows.isEmpty()) {
            appendLine("  пусто")
        } else {
            diagnostics.queueRows.forEach { row ->
                appendLine("  ${row.headline}")
                row.details.forEach { appendLine("    $it") }
            }
        }
        appendLine()

        appendLine("Фоновые задачи:")
        if (diagnostics.backgroundWork.isEmpty()) {
            appendLine("  нет")
        } else {
            diagnostics.backgroundWork.forEach { (name, state) -> appendLine("  $name = $state") }
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

    private fun megabytes(bytes: Long): String =
        if (bytes < 0) "неизвестно" else "${bytes / 1_048_576} МБ"

    private fun timestamp(at: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(at))
}
