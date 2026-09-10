package dev.wystore.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuStoreApiCompatibilityTest {
    @Test
    fun startsWithTheBakedInCodeAndKeepsBoundedFallbacks() {
        assertEquals(
            listOf(110910L, 1_000_000L, 247L),
            RuStoreApiCompatibilityPolicy.candidates()
        )
    }

    @Test
    fun migrationRejectsManifestVersionPreviouslyStoredAsApiCode() {
        assertEquals(110910L, RuStoreApiCompatibilityPolicy.migrateLegacyCode(1105002L))
        assertEquals(110502L, RuStoreApiCompatibilityPolicy.migrateLegacyCode(110502L))
        assertEquals(110910L, RuStoreApiCompatibilityPolicy.codeFromBackup(backupVersion = 1, versionCode = 1105002L))
        assertEquals(1200000L, RuStoreApiCompatibilityPolicy.codeFromBackup(backupVersion = 2, versionCode = 1200000L))
    }

    @Test
    fun cleanInstallCarriesTheBakedInCode() {
        assertEquals(110910L, RuStoreCompatibility().apiVersionCode)
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
