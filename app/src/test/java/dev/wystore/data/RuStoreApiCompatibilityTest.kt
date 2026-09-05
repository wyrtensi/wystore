package dev.wystore.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuStoreApiCompatibilityTest {
    @Test
    fun derivesApiCodeFromOfficialVersionNameInsteadOfManifestVersionCode() {
        assertEquals(110802L, RuStoreApiCompatibilityPolicy.fromOfficialVersionName("1.108.0.2"))
        assertEquals(110502L, RuStoreApiCompatibilityPolicy.fromOfficialVersionName("1.105.0.2"))
        assertNull(RuStoreApiCompatibilityPolicy.fromOfficialVersionName("1.108-beta"))
        assertNull(RuStoreApiCompatibilityPolicy.fromOfficialVersionName(null))
    }

    @Test
    fun startsWithDiscoveredCodeAndKeepsBoundedFallbacks() {
        assertEquals(
            listOf(110802L, 1108002L, 1_000_000L, 247L),
            RuStoreApiCompatibilityPolicy.candidates(preferred = 1108002L, discovered = 110802L)
        )
        assertEquals(
            listOf(110802L, 1_000_000L, 247L),
            RuStoreApiCompatibilityPolicy.candidates(preferred = 110802L, discovered = 110802L)
        )
    }

    @Test
    fun migrationRejectsManifestVersionPreviouslyStoredAsApiCode() {
        assertEquals(110802L, RuStoreApiCompatibilityPolicy.migrateLegacyCode(1105002L))
        assertEquals(110502L, RuStoreApiCompatibilityPolicy.migrateLegacyCode(110502L))
        assertEquals(110802L, RuStoreApiCompatibilityPolicy.codeFromBackup(backupVersion = 1, versionCode = 1105002L))
        assertEquals(1200000L, RuStoreApiCompatibilityPolicy.codeFromBackup(backupVersion = 2, versionCode = 1200000L))
    }

    @Test
    fun cleanInstallUsesCurrentlyVerifiedDefaultCode() {
        assertEquals(110802L, RuStoreCompatibility().apiVersionCode)
    }

    @Test
    fun retries419BeforeFallingBackAndDiscardsRejectedResponses() {
        val seen = mutableListOf<Long>()
        val rejected = mutableListOf<FakeResponse>()

        val result = RuStoreApiCompatibilityPolicy.execute(
            candidates = listOf(1108002L, 110802L),
            request = { code ->
                seen += code
                FakeResponse(if (code == 1108002L) 419 else 200)
            },
            statusCode = FakeResponse::status,
            discard = { rejected += it }
        )

        assertTrue(result.accepted)
        assertEquals(110802L, result.versionCode)
        assertEquals(200, result.value.status)
        assertEquals(listOf(1108002L, 1108002L, 1108002L, 110802L), seen)
        assertEquals(3, rejected.size)
    }

    @Test
    fun returnsLastResponseOpenWhenEveryCandidateIsRejected() {
        val rejected = mutableListOf<FakeResponse>()

        val result = RuStoreApiCompatibilityPolicy.execute(
            candidates = listOf(110802L, 247L),
            request = { FakeResponse(417) },
            statusCode = FakeResponse::status,
            discard = { rejected += it }
        )

        assertFalse(result.accepted)
        assertEquals(247L, result.versionCode)
        assertEquals(417, result.value.status)
        assertEquals(5, rejected.size)
    }

    private data class FakeResponse(val status: Int)
}
