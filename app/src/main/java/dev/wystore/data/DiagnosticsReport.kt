package dev.wystore.data

import dev.wystore.updates.FirmwareInstallFallback
import dev.wystore.updates.SessionInstallRejection
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
    val artifactBytes: Long,
    val notificationsGranted: Boolean,
    val notificationChannels: List<Pair<String, String>>,
    val canInstallUnknownApps: Boolean,
    val batteryOptimizationsIgnored: Boolean,
    val powerSaveMode: Boolean,
    val deviceIdleMode: Boolean,
    val standbyBucket: String,
    val vendorBackgroundSettings: String,
    /** Whether this device has been seen refusing installer sessions; see SessionInstallRejection. */
    val sessionsRefusedByFirmware: Boolean,
    /** The vendor shell as the build properties describe it, or that there is no sign of one. */
    val firmwareShell: String,
    val managedApps: Int,
    val managedBySource: List<Pair<String, Int>>,
    val managedWithoutAutoUpdate: Int,
    val managedForcedToStore: Int,
    val githubRepositories: Int,
    val queueRows: List<DiagnosticsQueueRow>,
    val downloadedNotInstalled: List<String>,
    val backgroundWork: List<Pair<String, String>>,
    val lastCheck: String?,
    val lastCheckProblems: List<String>,
    val settings: List<Pair<String, String>>,
    val events: List<DiagnosticsEvent>
)

/**
 * Renders a report a person can paste into an issue.
 *
 * Plain text on purpose: it is read by whoever receives it, not parsed. Everything it carries is
 * already on the device - nothing is fetched, no request is made on the way to building it - and it
 * carries no identifiers of the person: no account, no file paths, no URLs, and package names only
 * for the apps this store manages, which is what a queue problem is about.
 *
 * It is arranged by the questions people actually arrive with, so that whichever of them this
 * report is about, the answer is somewhere in the text:
 *
 *  - nothing downloads - the network and how the system counts it, Data Saver, the standby bucket,
 *    power saving, free space, and WorkManager's own state for the transfer;
 *  - it downloaded and then nothing happened - what is waiting to be installed, and whether this
 *    store may install at all;
 *  - the check finds nothing, or the wrong thing - which apps it could not check and why, how many
 *    apps are managed, by which source, and how many are excluded from automatic updates;
 *  - nothing arrives in the background - the periodic task's state, the battery rules, and the
 *    interval;
 *  - no notifications - every channel this app posts to and whether it is still switched on.
 *
 * It also carries the verdicts that belong to the system rather than to the store. The first report
 * of a queue that never moved said only that two rows were waiting: the failure had happened inside
 * WorkManager, before any of this app ran, so the store had nothing to log and the report read as
 * if nothing were wrong at all. What the store cannot see about itself is now asked of whoever can
 * see it.
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

        appendLine(
            "Способ установки: " + if (diagnostics.sessionsRefusedByFirmware) {
                "системный установщик (сессии отклоняются прошивкой)"
            } else {
                "сессия PackageInstaller"
            }
        )
        appendLine("Оболочка: ${diagnostics.firmwareShell}")
        val findings = findings(diagnostics)
        if (findings.isEmpty()) {
            appendLine("Распознано: ничего")
        } else {
            appendLine("Распознано:")
            findings.forEach { appendLine("  $it") }
        }
        appendLine()

        appendLine("Сеть: ${diagnostics.networkSummary}")
        appendLine("Экономия трафика: ${diagnostics.dataSaver}")
        appendLine("Механизм передачи: ${diagnostics.transferMechanism}")
        appendLine("Свободно под загрузки: ${megabytes(diagnostics.cacheFreeBytes)}")
        appendLine("Занято скачанными файлами: ${megabytes(diagnostics.artifactBytes)}")
        appendLine()

        appendLine("Уведомления: ${yesNo(diagnostics.notificationsGranted)}")
        diagnostics.notificationChannels.forEach { (name, state) -> appendLine("  $name: $state") }
        appendLine("Установка из неизвестных источников: ${yesNo(diagnostics.canInstallUnknownApps)}")
        appendLine("Без ограничений батареи: ${yesNo(diagnostics.batteryOptimizationsIgnored)}")
        appendLine("Энергосбережение: ${onOff(diagnostics.powerSaveMode)}")
        appendLine("Спящий режим (Doze): ${yesNo(diagnostics.deviceIdleMode)}")
        appendLine("Категория активности: ${diagnostics.standbyBucket}")
        appendLine("Свои правила автозапуска у прошивки: ${diagnostics.vendorBackgroundSettings}")
        appendLine()

        appendLine("Принятых приложений: ${diagnostics.managedApps}")
        diagnostics.managedBySource.forEach { (source, count) -> appendLine("  $source: $count") }
        appendLine("  без автообновления: ${diagnostics.managedWithoutAutoUpdate}")
        appendLine("  принудительно через Wy Store: ${diagnostics.managedForcedToStore}")
        appendLine("Репозиториев GitHub: ${diagnostics.githubRepositories}")
        appendLine()

        appendLine("Последняя проверка: ${diagnostics.lastCheck ?: "не было"}")
        if (diagnostics.lastCheckProblems.isNotEmpty()) {
            appendLine("Не удалось проверить:")
            diagnostics.lastCheckProblems.forEach { appendLine("  $it") }
        }
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

        appendLine("Скачано, но не установлено (${diagnostics.downloadedNotInstalled.size}):")
        if (diagnostics.downloadedNotInstalled.isEmpty()) {
            appendLine("  ничего")
        } else {
            diagnostics.downloadedNotInstalled.forEach { appendLine("  $it") }
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

    /**
     * Known situations read out of the failures, so the report names them instead of leaving them
     * to whoever happens to recognise the error line.
     *
     * The first report of MIUI refusing installs carried the answer four times over - the same
     * `INSTALL_FAILED_INTERNAL_ERROR: Permission Denied` against four apps - and nothing in it said
     * what that line means. It is counted from the log as well as from the stored flag, because a
     * report from a build that had not learnt it yet still shows the refusals.
     */
    fun findings(diagnostics: Diagnostics): List<String> = buildList {
        val refusals = diagnostics.events.count { event ->
            event.code == FirmwareInstallFallback.EVENT_SESSION_REFUSED ||
                SessionInstallRejection.isFirmwareRefusal(event.detail)
        }
        if (diagnostics.sessionsRefusedByFirmware || refusals > 0) {
            add(
                "прошивка отклоняет установку через сессию PackageInstaller" +
                    (if (refusals > 0) " ($refusals)" else "") +
                    " — так делает MIUI с включённой оптимизацией; " +
                    if (diagnostics.sessionsRefusedByFirmware) {
                        "установка идёт через системный установщик"
                    } else {
                        "эта версия ещё не переключилась на системный установщик"
                    }
            )
        }
        val refetched = diagnostics.events.count { it.code == FirmwareInstallFallback.EVENT_WHOLE_APK_REFETCH }
        if (refetched > 0) add("скачано заново одним APK вместо частей ($refetched)")
    }

    private fun onOff(value: Boolean) = if (value) "вкл" else "выкл"

    private fun yesNo(value: Boolean) = if (value) "да" else "нет"

    private fun megabytes(bytes: Long): String =
        if (bytes < 0) "неизвестно" else "${bytes / 1_048_576} МБ"

    private fun timestamp(at: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(at))
}
