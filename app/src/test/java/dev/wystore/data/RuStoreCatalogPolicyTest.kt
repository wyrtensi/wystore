package dev.wystore.data

import dev.wystore.data.RuStoreCatalogPolicy.Catalog
import dev.wystore.device.DeviceProfile
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class RuStoreCatalogPolicyTest {

    private lateinit var server: MockWebServer
    private val client = RuStoreApiClient(OkHttpClient())

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun call(profile: DeviceProfile) = client.execute(RuStoreCatalogPolicy.catalogues(profile)) { versionCode ->
        Request.Builder()
            .url(server.url("/applicationData/overallInfo/ru.example.app"))
            .header("ruStoreVerCode", versionCode.toString())
    }

    /** The guard the phone depends on: nothing about TV ever reaches a phone's request. */
    @Test
    fun `a phone asks only the phone catalogue and never sends the header`() {
        assertEquals(listOf(Catalog.PHONE), RuStoreCatalogPolicy.catalogues(DeviceProfile.PHONE))

        server.enqueue(MockResponse().setResponseCode(404))
        call(DeviceProfile.PHONE).use { assertEquals(404, it.code) }

        assertEquals(1, server.requestCount)
        assertNull(server.takeRequest().getHeader(RuStoreCatalogPolicy.DEVICE_TYPE_HEADER))
    }

    @Test
    fun `a TV asks the TV catalogue first`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        call(DeviceProfile.TV).use { assertEquals(200, it.code) }

        assertEquals(1, server.requestCount)
        assertEquals("TV", server.takeRequest().getHeader(RuStoreCatalogPolicy.DEVICE_TYPE_HEADER))
    }

    @Test
    fun `a TV falls back to the phone catalogue when the TV one does not have the app`() {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        call(DeviceProfile.TV).use { assertEquals(200, it.code) }

        assertEquals(2, server.requestCount)
        assertEquals("TV", server.takeRequest().getHeader(RuStoreCatalogPolicy.DEVICE_TYPE_HEADER))
        assertNull(server.takeRequest().getHeader(RuStoreCatalogPolicy.DEVICE_TYPE_HEADER))
    }

    @Test
    fun `a server error is not a reason to ask the other catalogue`() {
        server.enqueue(MockResponse().setResponseCode(503))
        call(DeviceProfile.TV).use { assertEquals(503, it.code) }

        assertEquals(1, server.requestCount)
    }

    @Test
    fun `not found in both catalogues stays not found`() {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(404))
        call(DeviceProfile.TV).use { assertEquals(404, it.code) }

        assertEquals(2, server.requestCount)
    }
}
