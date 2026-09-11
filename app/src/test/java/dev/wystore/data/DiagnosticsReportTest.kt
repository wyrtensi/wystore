package dev.wystore.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsReportTest {

    private fun diagnostics(
        rootAvailable: Boolean? = false,
        events: List<DiagnosticsEvent> = emptyList(),
        queueRows: List<DiagnosticsQueueRow> = emptyList(),
        downloadedNotInstalled: List<String> = emptyList(),
        backgroundWork: List<Pair<String, String>> = emptyList(),
        networkSummary: String = "Wi-Fi, лимитная: нет",
        dataSaver: String = "выкл",
        transferMechanism: String = "USER_INITIATED_JOB",
        notificationChannels: List<Pair<String, String>> = emptyList(),
        standbyBucket: String = "ACTIVE",
        managedBySource: List<Pair<String, Int>> = emptyList(),
        managedWithoutAutoUpdate: Int = 0,
        managedForcedToStore: Int = 0,
        lastCheckProblems: List<String> = emptyList()
    ) = Diagnostics(
        appVersionName = "0.1.25",
        appVersionCode = 26,
        applicationId = "app.wystore",
        targetSdk = 36,
        installerOfSelf = "dev.wystore",
        androidRelease = "16",
        sdkInt = 36,
        securityPatch = "2026-08-01",
        manufacturer = "Xiaomi",
        model = "2412DPC0AG",
        device = "rodin",
        abis = listOf("arm64-v8a"),
        screenWidthPx = 1220,
        screenHeightPx = 2712,
        densityDpi = 432,
        smallestWidthDp = 452,
        fontScale = 1.0f,
        locale = "ru_RU",
        rootAvailable = rootAvailable,
        rootSilentInstall = false,
        rootBackgroundDownloads = false,
        networkSummary = networkSummary,
        dataSaver = dataSaver,
        transferMechanism = transferMechanism,
        cacheFreeBytes = 12L * 1024 * 1024 * 1024,
        artifactBytes = 64L * 1024 * 1024,
        notificationsGranted = true,
        notificationChannels = notificationChannels,
        canInstallUnknownApps = true,
        batteryOptimizationsIgnored = true,
        powerSaveMode = false,
        deviceIdleMode = false,
        standbyBucket = standbyBucket,
        managedApps = 19,
        managedBySource = managedBySource,
        managedWithoutAutoUpdate = managedWithoutAutoUpdate,
        managedForcedToStore = managedForcedToStore,
        githubRepositories = 3,
        queueRows = queueRows,
        downloadedNotInstalled = downloadedNotInstalled,
        backgroundWork = backgroundWork,
        lastCheck = "9 сент. 2026 г. — по кнопке, проверено 17 из 19",
        lastCheckProblems = lastCheckProblems,
        settings = DiagnosticsSettingsDump.of(StoreSettings()),
        events = events
    )

    /** The three things anyone asks first: which Android, which device, is root there. */
    @Test
    fun theReportLeadsWithWhatIsAlwaysAskedFirst() {
        val text = DiagnosticsReport.render(diagnostics(), now = 0L)

        assertTrue(text.contains("Android 16 (SDK 36)"))
        assertTrue(text.contains("Xiaomi 2412DPC0AG"))
        assertTrue(text.contains("Root: нет"))
        assertTrue(text.contains("sw452dp"))
    }

    @Test
    fun anUncheckedRootStateIsNotReportedAsAbsent() {
        val text = DiagnosticsReport.render(diagnostics(rootAvailable = null), now = 0L)

        assertTrue(text.contains("Root: не проверялся"))
        assertFalse(text.contains("Root: нет"))
    }

    @Test
    fun everyEmptySectionSaysSoRatherThanVanishing() {
        val text = DiagnosticsReport.render(diagnostics(), now = 0L)

        assertTrue(text.contains("Очередь (0)"))
        assertTrue(text.contains("пусто"))
        assertTrue(text.contains("Скачано, но не установлено (0)"))
        assertTrue(text.contains("ничего"))
        assertTrue(text.contains("не записано"))
    }

    /**
     * The report that started this: two rows waiting, nothing in the log, and no way to tell that
     * WorkManager had already given up on both. What the system thinks has to be in the text, or
     * the report describes a healthy queue that happens not to be moving.
     */
    @Test
    fun aQueueThatNeverMovedSaysWhatTheSystemDidWithIt() {
        val text = DiagnosticsReport.render(
            diagnostics(
                queueRows = listOf(
                    DiagnosticsQueueRow(
                        headline = "com.avito.android 1.0 AVAILABLE",
                        details = listOf(
                            "источник: RUSTORE, позиция 0, приоритет 100",
                            "скачано: 0 / 0 МБ",
                            "задача: FAILED, попыток 1"
                        )
                    )
                ),
                backgroundWork = listOf("проверка (по кнопке)" to "FAILED, попыток 1")
            ),
            now = 0L
        )

        assertTrue(text.contains("  com.avito.android 1.0 AVAILABLE"))
        assertTrue(text.contains("    задача: FAILED, попыток 1"))
        assertTrue(text.contains("проверка (по кнопке) = FAILED, попыток 1"))
    }

    /** The conditions that stop a transfer without ever reaching the store's own error handling. */
    @Test
    fun theReportNamesEveryGateThatCanStallATransferSilently() {
        val text = DiagnosticsReport.render(
            diagnostics(
                networkSummary = "Wi-Fi, лимитная: да",
                dataSaver = "вкл, фоновый трафик запрещён",
                transferMechanism = "WORK_MANAGER_FOREGROUND",
                notificationChannels = listOf("канал передач" to "выключен"),
                standbyBucket = "RESTRICTED"
            ),
            now = 0L
        )

        assertTrue(text.contains("Сеть: Wi-Fi, лимитная: да"))
        assertTrue(text.contains("Экономия трафика: вкл, фоновый трафик запрещён"))
        assertTrue(text.contains("Механизм передачи: WORK_MANAGER_FOREGROUND"))
        assertTrue(text.contains("канал передач: выключен"))
        assertTrue(text.contains("Категория активности: RESTRICTED"))
        assertTrue(text.contains("Свободно под загрузки: 12288 МБ"))
        assertTrue(text.contains("Занято скачанными файлами: 64 МБ"))
    }

    /** "It downloaded and then nothing happened" has its own section, because it is its own bug. */
    @Test
    fun whatIsWaitingToBeInstalledIsListed() {
        val text = DiagnosticsReport.render(
            diagnostics(
                downloadedNotInstalled = listOf(
                    "org.telegram.messenger 11.2.0 (RUSTORE), файлов 1, скачано 10 сент. 2026 г."
                )
            ),
            now = 0L
        )

        assertTrue(text.contains("Скачано, но не установлено (1)"))
        assertTrue(text.contains("org.telegram.messenger 11.2.0 (RUSTORE)"))
    }

    /** "Why was my app not checked" is answered by name and reason, not by a count. */
    @Test
    fun aCheckThatCouldNotSeeAnAppSaysWhichOneAndWhy() {
        val text = DiagnosticsReport.render(
            diagnostics(
                lastCheckProblems = listOf("ru.beru.android — NOT_IN_CATALOGUE"),
                managedBySource = listOf("RUSTORE" to 16, "GITHUB" to 3),
                managedWithoutAutoUpdate = 2,
                managedForcedToStore = 1
            ),
            now = 0L
        )

        assertTrue(text.contains("Не удалось проверить:"))
        assertTrue(text.contains("ru.beru.android — NOT_IN_CATALOGUE"))
        assertTrue(text.contains("  RUSTORE: 16"))
        assertTrue(text.contains("без автообновления: 2"))
        assertTrue(text.contains("принудительно через Wy Store: 1"))
    }

    @Test
    fun failuresAppearWithTheirCodeAndDetail() {
        val text = DiagnosticsReport.render(
            diagnostics(
                events = listOf(
                    DiagnosticsEvent(
                        at = 0L,
                        packageName = "ru.beru.android",
                        code = "INTERNAL",
                        detail = "Another queue item is active"
                    )
                )
            ),
            now = 0L
        )

        assertTrue(text.contains("ru.beru.android INTERNAL: Another queue item is active"))
    }

    /** Every switch has to be in the text, including the ones nobody thought to print. */
    @Test
    fun theSettingsSectionCarriesTheOnesThatUsedToBeMissing() {
        val text = DiagnosticsReport.render(diagnostics(), now = 0L)

        assertTrue(text.contains("quietHoursEnabled = false"))
        assertTrue(text.contains("readyNotificationsEnabled = true"))
        assertTrue(text.contains("artifactRetentionDays = 7"))
        assertTrue(text.contains("themeMode = SYSTEM"))
    }
}
