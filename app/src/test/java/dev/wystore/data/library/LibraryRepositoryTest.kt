package dev.wystore.data.library

import java.nio.file.Files
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class LibraryRepositoryTest {
    private fun fixture(name: String) = javaClass.getResource("/library/$name.json")!!.readText()

    @Test fun `parallel reads share a snapshot and force refresh revalidates with ETag`() = runTest {
        MockWebServer().use { server ->
            server.start()
            val dir = Files.createTempDirectory("library-cache").toFile()
            try {
                server.enqueue(MockResponse().setBody(fixture("index")).setHeader("ETag", "v1"))
                val repo = LibraryRepository(OkHttpClient(), dir, server.url("/"))
                coroutineScope {
                    val a = async { repo.index() }
                    val b = async { repo.index() }
                    assertEquals(a.await().value, b.await().value)
                }
                assertEquals(1, server.requestCount)
                server.enqueue(MockResponse().setResponseCode(304))
                assertFalse(repo.index(forceRefresh = true).stale)
                server.takeRequest()
                assertEquals("v1", server.takeRequest().getHeader("If-None-Match"))
                val restored = LibraryRepository(OkHttpClient(), dir, server.url("/"))
                assertEquals(1, restored.index().value.applications.size)
                assertEquals(2, server.requestCount)
            } finally { dir.deleteRecursively() }
        }
    }

    @Test fun `invalid refresh preserves last valid data and exposes the error`() = runTest {
        MockWebServer().use { server ->
            server.start()
            val dir = Files.createTempDirectory("library-cache").toFile()
            try {
                server.enqueue(MockResponse().setBody(fixture("catalog")))
                val repo = LibraryRepository(OkHttpClient(), dir, server.url("/"))
                val good = repo.catalog().value
                server.enqueue(MockResponse().setBody("{\"schema\":2,\"apps\":[]}"))
                val stale = repo.catalog(forceRefresh = true)
                assertTrue(stale.stale)
                assertNotNull(stale.error)
                assertEquals(good, stale.value)
                val restored = LibraryRepository(OkHttpClient(), dir, server.url("/"))
                assertEquals(good, restored.catalog().value)
            } finally { dir.deleteRecursively() }
        }
    }

    @Test fun `first unavailable fetch fails instead of inventing empty catalogue`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setResponseCode(503))
            val dir = Files.createTempDirectory("library-cache").toFile()
            try {
                val result = runCatching { LibraryRepository(OkHttpClient(), dir, server.url("/")).catalog() }
                assertTrue(result.isFailure)
                assertFalse(dir.resolve("catalog.json").exists())
            } finally { dir.deleteRecursively() }
        }
    }
}
