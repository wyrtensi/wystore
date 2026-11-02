package dev.wystore.updates

import dev.wystore.data.ManagedSource
import dev.wystore.data.TransferFailure
import dev.wystore.data.library.*
import dev.wystore.data.local.UpdateArtifactEntity
import dev.wystore.data.local.UpdateQueueEntity
import dev.wystore.updates.model.QueueState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class QueueArtifactIntegrityTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `install time checks the full pinned set even if a member row disappears`() {
        val (row, artifacts) = request()
        QueueArtifactIntegrity.requireMatches(row, artifacts)
        assertThrows(TransferFailure::class.java) { QueueArtifactIntegrity.requireMatches(row, artifacts.take(1)) }
        assertThrows(TransferFailure::class.java) { QueueArtifactIntegrity.requireMatches(row, artifacts.map { it.copy(sourceHash = null) }) }
        assertThrows(TransferFailure::class.java) { QueueArtifactIntegrity.requireMatches(row.copy(libraryRequestJson = null), artifacts) }
    }

    @Test fun `same size corruption and a changed native version or source revision are rejected`() {
        val (row, artifacts) = request()
        assertThrows(TransferFailure::class.java) { QueueArtifactIntegrity.requireMatches(row.copy(versionCode = 101), artifacts) }
        assertThrows(TransferFailure::class.java) { QueueArtifactIntegrity.requireMatches(row.copy(sourceRevision = 4), artifacts) }
        java.io.File(artifacts.last().path).writeText("other")
        assertEquals(artifacts.last().size, java.io.File(artifacts.last().path).length())
        assertThrows(TransferFailure::class.java) { QueueArtifactIntegrity.requireMatches(row, artifacts) }
    }

    @Test fun `changing the selection revision preserves the exact selected publication`() {
        val (row, _) = request()
        val old = LibraryRequestCodec.decode(requireNotNull(row.libraryRequestJson))
        val revised = LibraryRequestCodec.decode(LibraryRequestCodec.withRevision(requireNotNull(row.libraryRequestJson), 9))
        assertEquals(9L, revised.sourceRevision)
        assertEquals(old.application, revised.application)
        assertEquals(old.variantId, revised.variantId)
    }

    private fun request(): Pair<UpdateQueueEntity, List<UpdateArtifactEntity>> {
        val original = LibraryJson.parseIndex(javaClass.getResource("/library/index.json")!!.readText()).applications.values.single()
        val files = listOf(temporary.newFile("base.apk").apply { writeText("base!") },
            temporary.newFile("split.apk").apply { writeText("split") })
        val descriptors = files.map { file ->
            val hash = LibraryFileIntegrity.sha256(file)
            LibraryArtifact("$hash.apk", hash, file.length(),
                "https://github.com/wyrtensi/wystore-library/releases/download/${original.packageName}-library/$hash.apk")
        }
        val members = descriptors.mapIndexed { index, artifact -> LibraryMember(artifact.sha256,
            if (index == 0) "base.apk" else "split_config.en.apk", if (index == 0) "base" else "split", if (index == 0) "" else "config.en") }
        val app = original.copy(artifacts = descriptors.associateBy { it.sha256 },
            variants = original.variants.map { it.copy(files = members) })
        val row = UpdateQueueEntity("pinned", app.packageName, app.name, "1", app.variants.single().versionCode,
            ManagedSource.WY_STORE.name, QueueState.READY_TO_INSTALL.name, sourceRevision = 3,
            libraryRequestJson = LibraryRequestCodec.encode(app, app.defaultVariant, 3))
        return row to files.mapIndexed { index, file -> UpdateArtifactEntity(row.id, file.path, file.length(), descriptors[index].sha256) }
    }
}
