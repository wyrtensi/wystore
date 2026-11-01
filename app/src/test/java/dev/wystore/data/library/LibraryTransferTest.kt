package dev.wystore.data.library

import dev.wystore.data.TransferFailure
import dev.wystore.updates.model.QueueErrorCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class LibraryTransferTest {
    @Test fun `metadata gating reaches the real APK endpoint only for a newer version`() = runTest {
        okhttp3.mockwebserver.MockWebServer().use { server ->
            server.start()
            server.enqueue(okhttp3.mockwebserver.MockResponse().setBody("apk-bytes"))
            val client = okhttp3.OkHttpClient()
            for (installed in listOf(100L, 101L, 99L)) {
                LibraryTransfer.download(version(), resolveLatest = { error("No refresh") },
                    allowed = { LibraryUpdatePolicy.isNewer(installed, it) }, onResolved = {}) {
                    client.newCall(okhttp3.Request.Builder().url(server.url("/base.apk")).build())
                        .execute().use { it.body!!.string() }
                }
            }
            assertEquals(1, server.requestCount)
            assertEquals("/base.apk", server.takeRequest().path)
        }
    }

    private fun application() = LibraryJson.parseIndex(javaClass.getResource("/library/index.json")!!.readText())
        .applications.getValue("org.example.library")
    private fun version() = LibraryVariantPolicy.resolve(application(), application().variants.single())

    @Test fun `request roundtrip preserves the selected set and source revision`() {
        val restored = LibraryRequestCodec.decode(LibraryRequestCodec.encode(application(), "modern", 4))
        assertEquals(4L, restored.sourceRevision)
        assertEquals(version(), restored.version())
        assertEquals(listOf("base.apk"), restored.version().members.map { it.originalName })
    }

    @Test fun `an altered stored URL is rejected by the canonical parser`() {
        val json = LibraryRequestCodec.encode(application(), "modern", 4)
        assertThrows(dev.wystore.data.SourceFormatException::class.java) {
            LibraryRequestCodec.decode(json.replace("github.com/wyrtensi/wystore-library", "evil.test/x/y"))
        }
    }

    @Test fun `equal or older installed version never calls the APK transport`() = runTest {
        for (installed in listOf(100L, 101L)) {
            var transfers = 0
            val result = LibraryTransfer.download(version(), resolveLatest = { error("No refresh expected") },
                allowed = { LibraryUpdatePolicy.isNewer(installed, it) }, onResolved = {}) {
                transfers++; "apk"
            }
            assertNull(result)
            assertEquals(0, transfers)
        }
    }

    @Test fun `missing asset refreshes once and stops if another store caught up`() = runTest {
        var transfers = 0; var refreshes = 0; var installed = 99L
        val result = LibraryTransfer.download(version(), resolveLatest = {
            refreshes++; installed = 101
            version().copy(versionCode = 101, publicationHash = "e".repeat(64))
        }, allowed = { LibraryUpdatePolicy.isNewer(installed, it) }, onResolved = {}) {
            transfers++
            throw TransferFailure(false, QueueErrorCode.SOURCE_CHANGED, detail = "removed", httpStatus = 404)
        }
        assertNull(result)
        assertEquals(1, refreshes)
        assertEquals(1, transfers)
    }

    @Test fun `a second missing asset fails without a refresh loop`() = runTest {
        var transfers = 0; var refreshes = 0
        val result = runCatching {
            LibraryTransfer.download(version(), resolveLatest = {
                refreshes++; version().copy(versionCode = 101, publicationHash = "e".repeat(64))
            }, allowed = { true }, onResolved = {}) {
                transfers++; throw TransferFailure(false, QueueErrorCode.SOURCE_CHANGED, detail = "removed", httpStatus = 404)
            }
        }
        assertTrue(result.isFailure)
        assertEquals(1, refreshes)
        assertEquals(2, transfers)
    }

    @Test fun `integrity failure never triggers publication fallback`() = runTest {
        var refreshes = 0
        val result = runCatching {
            LibraryTransfer.download(version(), resolveLatest = { refreshes++; version() },
                allowed = { true }, onResolved = {}) {
                throw TransferFailure(false, QueueErrorCode.INTEGRITY, detail = "hash mismatch")
            }
        }
        assertTrue(result.isFailure)
        assertEquals(0, refreshes)
    }
}
