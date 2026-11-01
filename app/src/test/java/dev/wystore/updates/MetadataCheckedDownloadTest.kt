package dev.wystore.updates

import dev.wystore.data.InstallSource
import dev.wystore.data.InstalledApp
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MetadataCheckedDownloadTest {
    @Test
    fun `library native versions are checked before the APK callback`() = runTest {
        var calls = 0
        for (version in listOf(19L, 20L, 21L)) {
            MetadataCheckedDownload.download(false, AvailableUpdateVersion.Library(version),
                readInstalled = { installed() }, isPaused = { false }) { calls++; "apk" }
        }
        assertEquals(1, calls)
    }

    private fun installed(code: Long = 20L, name: String = "2.0") = InstalledApp(
        packageName = "example.app", label = "Example", versionName = name,
        versionCode = code, lastUpdateTime = 1L, source = InstallSource.OTHER,
        signingDigests = emptySet()
    )

    @Test
    fun `RuStore downloads only a strictly newer metadata version`() = runTest {
        for ((code, expected) in listOf(19L to null, 20L to null, 21L to "apk", 0L to null)) {
            val result = MetadataCheckedDownload.download(
                userRequested = false,
                version = AvailableUpdateVersion.RuStore(code),
                readInstalled = { installed() },
                isPaused = { false }
            ) { "apk" }
            assertEquals("source version $code", expected, result)
        }
    }

    @Test
    fun `GitHub compares the selected tag and never uses release ids`() = runTest {
        // A release ID is absent by design: it cannot establish an Android version ordering.
        for ((tag, expected) in listOf("v1.9" to null, "v2.0" to null, "v2.1" to "apk", "nightly" to null, null to null)) {
            val result = MetadataCheckedDownload.download(
                userRequested = false,
                version = AvailableUpdateVersion.GitHub(tag),
                readInstalled = { installed(code = 987654321L) },
                isPaused = { false }
            ) { "apk" }
            assertEquals("selected tag $tag", expected, result)
        }
    }

    @Test
    fun `an unparseable installed GitHub version does not trigger an APK probe`() = runTest {
        assertNull(MetadataCheckedDownload.download(
            userRequested = false,
            version = AvailableUpdateVersion.GitHub("v3.0"),
            readInstalled = { installed(name = "development") },
            isPaused = { false }
        ) { "apk" })
    }

    @Test
    fun `a removed app is not automatically installed again`() = runTest {
        assertNull(MetadataCheckedDownload.download(
            userRequested = false,
            version = AvailableUpdateVersion.RuStore(30),
            readInstalled = { null },
            isPaused = { false }
        ) { "apk" })
    }

    @Test
    fun `an installed-state read failure stops before the transfer`() = runTest {
        var transferred = false
        try {
            MetadataCheckedDownload.download(
                userRequested = false,
                version = AvailableUpdateVersion.RuStore(30),
                readInstalled = { throw IllegalStateException("Package manager unavailable") },
                isPaused = { false }
            ) { transferred = true }
            throw AssertionError("The installed-state failure must propagate")
        } catch (expected: IllegalStateException) {
            assertEquals("Package manager unavailable", expected.message)
        }
        assertEquals(false, transferred)
    }

    @Test
    fun `Play pause blocks an automatic transfer even when its version is newer`() = runTest {
        assertNull(MetadataCheckedDownload.download(
            userRequested = false,
            version = AvailableUpdateVersion.RuStore(30),
            readInstalled = { installed() },
            isPaused = { true }
        ) { "apk" })
    }

    @Test
    fun `explicit reinstall retains the same version and exact handover download`() = runTest {
        assertEquals("selected apk", MetadataCheckedDownload.download(
            userRequested = true,
            version = AvailableUpdateVersion.GitHub(null),
            readInstalled = { installed() },
            isPaused = { true }
        ) { "selected apk" })
    }

    @Test
    fun `a stale RuStore queued version sends no request to the APK endpoint`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("apk bytes"))
            server.start()
            val client = OkHttpClient()
            // The queue found 20 earlier. Another installer has now installed 20 or 21.
            for (currentCode in listOf(20L, 21L)) {
                val result = MetadataCheckedDownload.download(
                    userRequested = false,
                    version = AvailableUpdateVersion.RuStore(20),
                    readInstalled = { installed(code = currentCode) },
                    isPaused = { false }
                ) {
                    client.newCall(Request.Builder().url(server.url("/update.apk")).build())
                        .execute().use { it.body!!.string() }
                }
                assertNull(result)
            }
            assertEquals(0, server.requestCount)
            // A real update takes the same path and actually reaches the endpoint.
            assertEquals("apk bytes", MetadataCheckedDownload.download(
                userRequested = false,
                version = AvailableUpdateVersion.RuStore(22),
                readInstalled = { installed(code = 21) },
                isPaused = { false }
            ) {
                client.newCall(Request.Builder().url(server.url("/update.apk")).build())
                    .execute().use { it.body!!.string() }
            })
            assertEquals("/update.apk", server.takeRequest().path)
        }
    }

    @Test
    fun `GitHub fallback to an older selected release sends no APK request`() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("apk bytes"))
            server.start()
            val client = OkHttpClient()
            // The queued release was 3.0, but its missing asset falls back to release 1.9.
            assertNull(MetadataCheckedDownload.download(
                userRequested = false,
                version = AvailableUpdateVersion.GitHub("v1.9"),
                readInstalled = { installed() },
                isPaused = { false }
            ) {
                client.newCall(Request.Builder().url(server.url("/fallback.apk")).build())
                    .execute().use { it.body!!.string() }
            })
            assertEquals(0, server.requestCount)
        }
    }
}
