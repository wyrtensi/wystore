package dev.wystore.data.local

import androidx.test.platform.app.InstrumentationRegistry
import dev.wystore.data.ManagedSource
import dev.wystore.data.StoreRepository
import dev.wystore.data.library.*
import dev.wystore.updates.QueueRepository
import dev.wystore.updates.model.QueueState
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LibraryRequestRetentionTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun readyFilesStayBoundToTheSelectedVariantAfterRediscovery() = checkRetained(QueueState.READY_TO_INSTALL)
    @Test fun runningDownloadStaysBoundToTheSelectedVariantAfterRediscovery() = checkRetained(QueueState.DOWNLOADING)

    @Test fun persistedDownloadProgressIncludesTheWholeApkSet() = runBlocking {
        val row = UpdateQueueEntity("split-progress", "org.example.progress", "Progress", "1", 1,
            ManagedSource.WY_STORE.name, QueueState.DOWNLOADING.name)
        val database = WyStoreDatabase.getInstance(context)
        val dao = database.updateQueueDao
        try {
            dao.insertOrReplace(row)
            assertTrue(QueueRepository.getInstance(context).recordProgress(row.id, 50, 100, 2, 4))
            val saved = requireNotNull(dao.getById(row.id)).toSnapshot()
            assertEquals(2, saved.artifactIndex)
            assertEquals(4, saved.artifactCount)
            assertEquals(50L, saved.downloadedBytes)
        } finally { dao.deleteById(row.id) }
    }

    @Test fun aNewSourceRevisionDoesNotReuseACanceledSelection() = runBlocking {
        val app = LibraryJson.parseIndex(instrumentation.context.assets.open("index.json").bufferedReader().use { it.readText() })
            .applications.values.single()
        val repository = StoreRepository(context)
        val revision = repository.sourceRevision(app.packageName)
        val dao = WyStoreDatabase.getInstance(context).updateQueueDao
        val old = UpdateQueueEntity("retention-old-revision", app.packageName, app.name, "1", app.variants.single().versionCode,
            ManagedSource.WY_STORE.name, QueueState.CANCELED.name, sourceRevision = revision,
            errorCode = dev.wystore.updates.model.QueueErrorCode.SOURCE_CHANGED.name,
            libraryRequestJson = LibraryRequestCodec.encode(app, app.defaultVariant, revision))
        var created: String? = null
        try {
            dao.insertOrReplace(old)
            val nextRevision = repository.advanceSourceRevision(app.packageName)
            val next = requireNotNull(QueueRepository.getInstance(context).enqueueLibraryUpdate(app, app.defaultVariant,
                sourceRevision = nextRevision))
            created = next.id
            assertNotEquals(old.id, next.id)
            assertEquals(QueueState.AVAILABLE.name, next.state)
            assertEquals(nextRevision, LibraryRequestCodec.decode(requireNotNull(next.libraryRequestJson)).sourceRevision)
        } finally { created?.let { dao.deleteById(it) }; dao.deleteById(old.id) }
    }

    private fun checkRetained(state: QueueState) = runBlocking {
        val original = LibraryJson.parseIndex(instrumentation.context.assets.open("index.json").bufferedReader().use { it.readText() })
            .applications.values.single()
        val hash = "f".repeat(64)
        val alternative = original.variants.single().copy(id = "alternative",
            files = listOf(LibraryMember(hash, "base.apk", "base", "")))
        val app = original.copy(variants = original.variants + alternative, artifacts = original.artifacts +
            (hash to LibraryArtifact("$hash.apk", hash, 100,
                "https://github.com/wyrtensi/wystore-library/releases/download/${original.packageName}-library/$hash.apk")))
        val revision = StoreRepository(context).sourceRevision(app.packageName)
        val dao = WyStoreDatabase.getInstance(context).updateQueueDao
        val file = File.createTempFile("pinned-request-", ".apk", context.cacheDir).apply { writeBytes(ByteArray(100)) }
        val row = UpdateQueueEntity("retention-${state.name}", app.packageName, app.name, "1", alternative.versionCode,
            ManagedSource.WY_STORE.name, state.name, sourceRevision = revision,
            libraryRequestJson = LibraryRequestCodec.encode(app, original.defaultVariant, revision))
        val artifact = UpdateArtifactEntity(row.id, file.path, file.length(), original.variants.single().files.single().artifact)
        try {
            dao.insertOrReplace(row)
            dao.insertArtifacts(listOf(artifact))
            val rediscovered = QueueRepository.getInstance(context).enqueueLibraryUpdate(app, alternative.id, sourceRevision = revision)
            assertEquals(row.id, rediscovered?.id)
            assertEquals(row.libraryRequestJson, dao.getById(row.id)?.libraryRequestJson)
            assertEquals(row.state, dao.getById(row.id)?.state)
            assertEquals(listOf(artifact), dao.getArtifactsForQueue(row.id))
            assertArrayEquals(ByteArray(100), file.readBytes())
        } finally { dao.deleteById(row.id); file.delete() }
    }
}
