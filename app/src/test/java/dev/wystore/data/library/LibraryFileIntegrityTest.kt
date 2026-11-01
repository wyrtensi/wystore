package dev.wystore.data.library

import dev.wystore.data.TransferFailure
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class LibraryFileIntegrityTest {
    @Test fun `every file is hashed again even when its cached size is unchanged`() {
        val directory = Files.createTempDirectory("library-integrity").toFile()
        try {
            val file = directory.resolve("base.apk").apply { writeText("abc") }
            val artifact = LibraryArtifact("base.apk", "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", 3, "unused")
            LibraryFileIntegrity.requireMatches(listOf(file), listOf(artifact))
            file.writeText("abd")
            assertThrows(TransferFailure::class.java) {
                LibraryFileIntegrity.requireMatches(listOf(file), listOf(artifact))
            }
            assertThrows(TransferFailure::class.java) {
                LibraryFileIntegrity.requireMatches(emptyList(), listOf(artifact))
            }
        } finally { directory.deleteRecursively() }
    }
}
