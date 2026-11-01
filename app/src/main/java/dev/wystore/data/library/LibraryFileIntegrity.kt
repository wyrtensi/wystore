package dev.wystore.data.library

import dev.wystore.data.TransferFailure
import dev.wystore.updates.model.QueueErrorCode
import java.io.File
import java.security.MessageDigest

object LibraryFileIntegrity {
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun requireMatches(files: List<File>, artifacts: List<LibraryArtifact>) {
        if (files.size != artifacts.size || files.isEmpty()) fail()
        files.zip(artifacts).forEach { (file, artifact) ->
            if (!file.isFile || file.length() != artifact.size || sha256(file) != artifact.sha256) fail()
        }
    }

    private fun fail(): Nothing = throw TransferFailure(false, QueueErrorCode.INTEGRITY,
        detail = "The complete library file set no longer matches its hashes")
}
