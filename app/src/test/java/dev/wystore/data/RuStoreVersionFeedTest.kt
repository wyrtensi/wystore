package dev.wystore.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuStoreVersionFeedTest {

    @Test
    fun parsesValidFeedDocument() {
        val parsed = RuStoreVersionFeed.parse(
            """
            {
              "schemaVersion": 1,
              "versionCode": 1114003,
              "versionName": "1.114.0.3",
              "verifiedZipTransport": true,
              "checkedAt": "2026-11-22T15:00:00Z",
              "source": "rustore_apk"
            }
            """.trimIndent()
        )
        assertEquals(RemoteRuStoreVersion(1_114_003L, "1.114.0.3"), parsed)
    }

    @Test
    fun rejectsLegacySixDigitOutOfRangeOrMalformedDocuments() {
        // Legacy 6-digit code (< 1_059_001) puts RuStore back on uncompressed .apk mode.
        assertNull(RuStoreVersionFeed.parse("""{"schemaVersion":1,"versionCode":111204,"versionName":"1.112.0.4"}"""))
        assertNull(RuStoreVersionFeed.parse("""{"schemaVersion":1,"versionCode":0}"""))
        assertNull(RuStoreVersionFeed.parse("""{"schemaVersion":1,"versionCode":-1}"""))
        assertNull(RuStoreVersionFeed.parse("""{"schemaVersion":1,"versionCode":1114003,"verifiedZipTransport":false}"""))
        assertNull(RuStoreVersionFeed.parse("""{"schemaVersion":2,"versionCode":1114003}"""))
        assertNull(RuStoreVersionFeed.parse("not json"))
    }

    @Test
    fun refreshScheduleRespectsDailyTtlFailureBackoffAndForcedCooldown() {
        val hour = 60L * 60 * 1000
        val minute = 60L * 1000
        val t0 = 1_700_000_000_000L

        assertTrue(RuStoreVersionFeed.shouldRefresh(t0, lastSuccessAtMs = null, lastAttemptAtMs = null, force = false))
        assertFalse(RuStoreVersionFeed.shouldRefresh(t0 + 12 * hour, lastSuccessAtMs = t0, lastAttemptAtMs = t0, force = false))
        assertTrue(RuStoreVersionFeed.shouldRefresh(t0 + 24 * hour, lastSuccessAtMs = t0, lastAttemptAtMs = t0, force = false))

        // Failed attempt backs off for an hour during normal traffic.
        assertFalse(RuStoreVersionFeed.shouldRefresh(t0 + 30 * minute, lastSuccessAtMs = null, lastAttemptAtMs = t0, force = false))
        assertTrue(RuStoreVersionFeed.shouldRefresh(t0 + hour, lastSuccessAtMs = null, lastAttemptAtMs = t0, force = false))

        // Forced refresh (after 417/419) ignores daily TTL and failure backoff, but keeps 5m cooldown.
        assertFalse(RuStoreVersionFeed.shouldRefresh(t0 + 2 * minute, lastSuccessAtMs = t0, lastAttemptAtMs = t0, force = true))
        assertTrue(RuStoreVersionFeed.shouldRefresh(t0 + 5 * minute, lastSuccessAtMs = t0, lastAttemptAtMs = t0, force = true))
    }
}
