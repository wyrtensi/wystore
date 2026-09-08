package dev.wystore.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsReportTest {

    private fun diagnostics(
        rootAvailable: Boolean? = false,
        events: List<DiagnosticsEvent> = emptyList(),
        queueRows: List<String> = emptyList()
    ) = Diagnostics(
        appVersionName = "0.1.25",
        appVersionCode = 26,
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
        rootAvailable = rootAvailable,
        rootSilentInstall = false,
        rootBackgroundDownloads = false,
        notificationsGranted = true,
        canInstallUnknownApps = true,
        batteryOptimizationsIgnored = true,
        managedApps = 19,
        githubRepositories = 3,
        queueRows = queueRows,
        lastCheck = "9 сент. 2026 г. — проверено 17",
        settings = listOf("wifiOnly" to "true"),
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
    fun anEmptyQueueAndAnEmptyLogSayThatRatherThanNothing() {
        val text = DiagnosticsReport.render(diagnostics(), now = 0L)

        assertTrue(text.contains("Очередь (0)"))
        assertTrue(text.contains("пусто"))
        assertTrue(text.contains("не записано"))
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
}
