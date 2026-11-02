package dev.wystore.updates

import dev.wystore.data.ManagedSource
import dev.wystore.data.TransferFailure
import dev.wystore.data.library.LibraryFileIntegrity
import dev.wystore.data.library.LibraryRequestCodec
import dev.wystore.data.local.UpdateArtifactEntity
import dev.wystore.data.local.UpdateQueueEntity
import dev.wystore.updates.model.QueueErrorCode
import java.io.File

/** Retain the selected complete set through storage, removal, process death and either installer. */
object QueueArtifactIntegrity {
    fun requireMatches(row: UpdateQueueEntity, artifacts: List<UpdateArtifactEntity>): List<File> {
        if (row.source != ManagedSource.WY_STORE.name) return artifacts.map { File(it.path) }
        val request = LibraryRequestCodec.decode(row.libraryRequestJson ?: fail())
        val expected = request.version()
        if (request.sourceRevision != row.sourceRevision || expected.packageName != row.packageName ||
            expected.versionCode != row.versionCode || artifacts.size != expected.artifacts.size) fail()
        val byHash = artifacts.associateBy { it.sourceHash ?: fail() }
        if (byHash.size != artifacts.size || byHash.keys != expected.artifacts.map { it.sha256 }.toSet()) fail()
        val files = expected.artifacts.map { File(byHash.getValue(it.sha256).path) }
        LibraryFileIntegrity.requireMatches(files, expected.artifacts)
        return files
    }

    private fun fail(): Nothing = throw TransferFailure(false, QueueErrorCode.INTEGRITY,
        detail = "The persisted library installation differs from its selected complete set")
}
