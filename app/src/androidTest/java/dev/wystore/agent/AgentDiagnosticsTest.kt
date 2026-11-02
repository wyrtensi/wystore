package dev.wystore.agent

import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class AgentDiagnosticsTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun adbShellCanReadTheSameDiagnosticsWithoutOpeningAScreen() {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(
            "content read --uri content://app.wystore.agent/diagnostics")
        val text = ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
        assertTrue(text, text.contains("Wy Store"))
        assertTrue(text, text.contains("app.wystore"))
        assertTrue(text, text.contains("Android"))
        assertFalse(text, text.contains("FileNotFoundException"))
    }

    @Test fun anApplicationUidCannotReadDiagnostics() {
        assertThrows(SecurityException::class.java) {
            instrumentation.targetContext.contentResolver.openFileDescriptor(
                Uri.parse("content://app.wystore.agent/diagnostics"), "r")?.close()
        }
    }
}
