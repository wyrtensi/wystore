package dev.wystore.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuStoreApiCompatibilityTest {
    @Test
    fun startsWithTheBakedInCodeAndKeepsBoundedFallbacks() {
        assertEquals(
            listOf(1_114_003L, 1_112_004L, 1_000_000L, 247L),
            RuStoreApiCompatibilityPolicy.candidates()
        )
    }

    @Test
    fun prefersNewerRemoteCodeFromGitHubFeedAndIgnoresLegacySixDigitCodes() {
        assertEquals(
            listOf(1_120_005L, 1_114_003L, 1_112_004L, 1_000_000L, 247L),
            RuStoreApiCompatibilityPolicy.candidates(remoteVersionCode = 1_120_005L)
        )
        // Older usable remote code still sits behind the baked-in code.
        assertEquals(
            listOf(1_114_003L, 1_110_000L, 1_112_004L, 1_000_000L, 247L),
            RuStoreApiCompatibilityPolicy.candidates(remoteVersionCode = 1_110_000L)
        )
        // Legacy 6-digit code (< 1_059_001) is ignored so RuStore keeps serving .zip transport.
        assertEquals(
            listOf(1_114_003L, 1_112_004L, 1_000_000L, 247L),
            RuStoreApiCompatibilityPolicy.candidates(remoteVersionCode = 111_204L)
        )
    }

    @Test
    fun migrationUpgradesOldCodesToCurrentManifestVersionCode() {
        assertEquals(1_114_003L, RuStoreApiCompatibilityPolicy.migrateLegacyCode(1105002L))
        assertEquals(1_114_003L, RuStoreApiCompatibilityPolicy.migrateLegacyCode(110502L))
        assertEquals(1_200_000L, RuStoreApiCompatibilityPolicy.migrateLegacyCode(1_200_000L))
        assertEquals(1_114_003L, RuStoreApiCompatibilityPolicy.codeFromBackup(backupVersion = 1, versionCode = 1105002L))
        assertEquals(1_200_000L, RuStoreApiCompatibilityPolicy.codeFromBackup(backupVersion = 2, versionCode = 1_200_000L))
    }

    @Test
    fun cleanInstallCarriesTheBakedInCode() {
        assertEquals(1_114_003L, RuStoreCompatibility().apiVersionCode)
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
