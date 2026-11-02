package dev.wystore.data.library

import dev.wystore.data.ArchiveIdentity
import org.junit.Assert.*
import org.junit.Test

class LibraryArchivePolicyTest {
    private val app = LibraryJson.parseIndex(javaClass.getResource("/library/index.json")!!.readText())
        .applications.getValue("org.example.library")
    private val version = LibraryVariantPolicy.resolve(app, app.variants.single())
    private val identity = ArchiveIdentity(app.packageName, "listing text", version.versionCode, null, setOf("signer"))

    @Test fun `selected native version and full file set must match before staging or installing`() {
        assertTrue(LibraryArchivePolicy.matches(version, identity, version.members.size))
        assertFalse(LibraryArchivePolicy.matches(version, identity.copy(versionCode = identity.versionCode + 1), version.members.size))
        assertFalse(LibraryArchivePolicy.matches(version, identity.copy(packageName = "other.package"), version.members.size))
        assertFalse(LibraryArchivePolicy.matches(version, identity, 0))
        assertFalse(LibraryArchivePolicy.matches(version, identity, version.members.size + 1))
    }
}
