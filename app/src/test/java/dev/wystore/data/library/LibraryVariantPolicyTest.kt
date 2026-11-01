package dev.wystore.data.library

import org.junit.Assert.*
import org.junit.Test

class LibraryVariantPolicyTest {
    @Test fun `base is first for root and session installers even when feed members are shuffled`() {
        val base = app()
        val split = LibraryMember("f".repeat(64), "split_config.arm64.apk", "split", "config.arm64")
        val source = base.copy(artifacts = base.artifacts + (split.artifact to
            LibraryArtifact("${split.artifact}.apk", split.artifact, 10, "https://example.test/split.apk")),
            variants = base.variants.map { it.copy(files = listOf(split) + it.files) })
        val result = selected(source)
        assertEquals(listOf("base", "split"), result.members.map { it.role })
        assertEquals(listOf("a".repeat(64), "f".repeat(64)), result.artifacts.map { it.sha256 })
    }

    private fun app() = LibraryJson.parseIndex(javaClass.getResource("/library/index.json")!!.readText())
        .applications.getValue("org.example.library")
    private fun device(abis: List<String> = listOf("arm64-v8a"), sdk: Int = 35) =
        LibraryDeviceSpec(sdk, abis, 480, listOf("ru_RU"), emptySet(), 0)
    private fun selected(app: LibraryApplication, device: LibraryDeviceSpec = device()) =
        (LibraryVariantPolicy.select(app, device) as LibrarySelection.Compatible).version

    @Test fun `older compatible cohort wins over unsupported newer default`() {
        val base = app()
        val newer = base.variants.single().copy(compatibility = base.variants.single().compatibility.copy(minSdk = 35))
        val older = newer.copy(id = "legacy", versionCode = 90,
            compatibility = newer.compatibility.copy(minSdk = 26, nativeAbis = setOf("armeabi-v7a")))
        assertEquals("legacy", selected(base.copy(variants = listOf(newer, older)),
            device(listOf("armeabi-v7a"), 28)).variantId)
    }

    @Test fun `equal older and newer metadata use native version only`() {
        val version = selected(app())
        assertFalse(LibraryUpdatePolicy.isNewer(100, version))
        assertFalse(LibraryUpdatePolicy.isNewer(101, version))
        assertTrue(LibraryUpdatePolicy.isNewer(99, version))
    }

    @Test fun `device density differs from sampled density without losing the complete set`() {
        val version = selected(app())
        assertEquals(listOf("base.apk"), version.members.map { it.originalName })
        assertEquals(100L, version.totalBytes)
        assertEquals("c".repeat(64), version.publicationHash)
    }

    @Test fun `x86 does not assume an ARM translation layer`() {
        assertTrue(LibraryVariantPolicy.select(app(), device(listOf("x86_64"))) is LibrarySelection.Unsupported)
        val javaOnly = app().let { base -> base.copy(variants = base.variants.map {
            it.copy(compatibility = it.compatibility.copy(nativeAbis = emptySet()))
        }) }
        assertEquals("modern", selected(javaOnly, device(listOf("x86_64"))).variantId)
    }

    @Test fun `missing features graphics and split dependencies refuse installation`() {
        val base = app()
        val c = base.variants.single().compatibility
        val incompatible = listOf(c.copy(requiredFeatures = setOf("android.hardware.camera")),
            c.copy(glEsVersion = 196608), c.copy(requiredSplitTypes = setOf("base__abi")),
            c.copy(usesSplits = setOf("missing.feature")))
        incompatible.forEach { bad ->
            assertTrue(LibraryVariantPolicy.select(base.copy(variants = listOf(base.variants.single().copy(
                compatibility = bad))), device()) is LibrarySelection.Unsupported)
        }
    }

    @Test fun `ranking prefers version then ABI locale density and stable id`() {
        val base = app(); val ru = base.variants.single()
        val en = ru.copy(id = "english", compatibility = ru.compatibility.copy(locale = "en_US", languages = setOf("en")))
        val dense = ru.copy(id = "density_match", compatibility = ru.compatibility.copy(density = 480))
        assertEquals("density_match", selected(base.copy(variants = listOf(en, ru, dense))).variantId)
        assertEquals("english", selected(base.copy(variants = listOf(en.copy(versionCode = 101), dense))).variantId)
        assertEquals("aaa", selected(base.copy(variants = listOf(ru.copy(id = "zzz"), ru.copy(id = "aaa")))).variantId)
    }

    @Test fun `signer metadata for another SDK is not a claim about this device`() {
        val base = app()
        val version = selected(base.copy(variants = base.variants.map { it.copy(certificateScopeSdk = 35) }))
        assertEquals(setOf("b".repeat(64)), version.preliminaryCertificates(35))
        assertTrue(version.preliminaryCertificates(34).isEmpty())
    }
}
