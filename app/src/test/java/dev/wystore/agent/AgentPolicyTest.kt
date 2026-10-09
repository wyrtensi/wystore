package dev.wystore.agent

import com.google.gson.JsonParser
import dev.wystore.data.ManagedSource
import dev.wystore.updates.model.QueueErrorCode
import dev.wystore.updates.model.QueueItemSnapshot
import dev.wystore.updates.model.QueueState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentPolicyTest {

    @Test
    fun onlyShellAndRootMayCall() {
        assertTrue(AgentPolicy.isAllowedCaller(2000))
        assertTrue(AgentPolicy.isAllowedCaller(0))
        assertFalse(AgentPolicy.isAllowedCaller(10234))
        assertFalse(AgentPolicy.isAllowedCaller(1000))
    }

    @Test
    fun packageListIsSplitTrimmedAndDeduplicated() {
        val parsed = AgentPolicy.parsePackages(" ru.one , ru.two\nru.one  ru.three ")
        assertEquals(listOf("ru.one", "ru.two", "ru.three"), parsed.valid)
        assertEquals(emptyList<String>(), parsed.invalid)
    }

    @Test
    fun invalidNamesAreReportedNotQueued() {
        val parsed = AgentPolicy.parsePackages("ru.ok,../etc,noDot,")
        assertEquals(listOf("ru.ok"), parsed.valid)
        assertEquals(listOf("../etc", "noDot"), parsed.invalid)
    }

    @Test
    fun aListLongerThanTheLimitIsCut() {
        val names = (1..AgentPolicy.MAX_PACKAGES + 5).joinToString(",") { "ru.app$it" }
        val parsed = AgentPolicy.parsePackages(names)
        assertEquals(AgentPolicy.MAX_PACKAGES, parsed.valid.size)
        assertEquals(5, parsed.overLimit.size)
    }

    @Test
    fun installIsConfirmedOnlyForTheVerifiedSignatureAndVersion() {
        val digests = setOf("aa", "bb")
        assertTrue(AgentPolicy.confirmsInstall(20, digests, installedVersionCode = 20, installedDigests = digests))
        assertTrue(AgentPolicy.confirmsInstall(20, digests, installedVersionCode = 21, installedDigests = digests))
        assertFalse(AgentPolicy.confirmsInstall(20, digests, installedVersionCode = 19, installedDigests = digests))
        assertFalse(AgentPolicy.confirmsInstall(20, digests, installedVersionCode = 20, installedDigests = setOf("aa")))
        assertFalse(AgentPolicy.confirmsInstall(20, digests, installedVersionCode = null, installedDigests = emptySet()))
        // A row without a verified identity cannot vouch for anything.
        assertFalse(AgentPolicy.confirmsInstall(20, emptySet(), installedVersionCode = 20, installedDigests = emptySet()))
    }

    @Test
    fun statusCarriesWhatTheAgentNeeds() {
        val row = QueueItemSnapshot(
            id = "q1", packageName = "ru.one", label = "One", versionName = "2.0", versionCode = 20,
            source = ManagedSource.RUSTORE, state = QueueState.FAILED, priority = 10, position = 0,
            downloadedBytes = 5, totalBytes = 10, errorCode = QueueErrorCode.NETWORK, errorDetail = "HTTP 500"
        )
        val json = JsonParser.parseString(
            AgentPolicy.statusJson(listOf(AgentPolicy.RowStatus(row, listOf(123L, 45L))))
        ).asJsonObject
        val item = json.getAsJsonArray("rows")[0].asJsonObject
        assertEquals("q1", item["id"].asString)
        assertEquals("ru.one", item["package"].asString)
        assertEquals("FAILED", item["state"].asString)
        assertEquals("NETWORK", item["error"].asString)
        assertEquals("HTTP 500", item["detail"].asString)
        assertEquals(20L, item["versionCode"].asLong)
        val artifacts = item.getAsJsonArray("artifacts")
        assertEquals(2, artifacts.size())
        assertEquals("content://app.wystore.agent/artifact/q1/1", artifacts[1].asJsonObject["uri"].asString)
        assertEquals(45L, artifacts[1].asJsonObject["size"].asLong)
    }
}
