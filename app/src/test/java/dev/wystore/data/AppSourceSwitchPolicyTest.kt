package dev.wystore.data

import dev.wystore.updates.model.QueueState
import org.junit.Assert.*
import org.junit.Test

class AppSourceSwitchPolicyTest {
    @Test fun `source revision survives the uninstall gap of a replacement`() {
        assertFalse(SourceRequestPolicy.isCurrent(null, ManagedSource.RUSTORE, 7, lastKnownRevision = 8))
        assertTrue(SourceRequestPolicy.isCurrent(null, ManagedSource.WY_STORE, 8, lastKnownRevision = 8))
    }

    @Test fun `stale settings edits cannot restore an earlier source selection`() {
        val current = ManagedApp("org.example.app", "Example", setOf("a"), source = ManagedSource.WY_STORE, sourceRevision = 7)
        val stale = current.copy(source = ManagedSource.RUSTORE, sourceRevision = 6, autoUpdate = false)
        val kept = SourceRequestPolicy.preserveSelection(current, stale)
        assertEquals(ManagedSource.WY_STORE, kept.source)
        assertEquals(7L, kept.sourceRevision)
        assertFalse(kept.autoUpdate)
    }

    private val installed = InstalledApp("org.example.app", "Example", "1", 100, 0,
        InstallSource.WY_STORE, setOf("a"))
    private val choice = AppSourceChoice.Library("org.example.app")

    @Test fun `compatible and unknown choices do not demand reinstallation`() {
        for (signature in listOf(SignatureCompatibility.COMPATIBLE, SignatureCompatibility.UNKNOWN,
                SignatureCompatibility.SOURCE_UNCONFIRMED)) {
            assertEquals(SourceSwitchDecision.SAVE_CHOICE,
                AppSourceSwitchPolicy.decide(installed, choice, signature, false))
        }
    }

    @Test fun `different identity needs verification before any replacement`() {
        assertEquals(SourceSwitchDecision.VERIFY_FOR_REPLACEMENT,
            AppSourceSwitchPolicy.decide(installed, choice, SignatureCompatibility.MISMATCH, false))
        assertEquals(SourceSwitchDecision.INVALID,
            AppSourceSwitchPolicy.decide(installed, AppSourceChoice.Library("org.other.app"), SignatureCompatibility.COMPATIBLE, false))
    }

    @Test fun `install already handed to Android defers source selection`() {
        assertEquals(SourceSwitchDecision.DEFER_UNTIL_INSTALL_SETTLES,
            AppSourceSwitchPolicy.decide(installed, choice, SignatureCompatibility.COMPATIBLE, true))
        assertTrue(SourceRequestPolicy.blocksSwitch(listOf(QueueState.INSTALLING), emptyList()))
        assertTrue(SourceRequestPolicy.blocksSwitch(listOf(QueueState.AWAITING_USER_CONFIRMATION), listOf("COMMITTED")))
        assertFalse(SourceRequestPolicy.blocksSwitch(listOf(QueueState.READY_TO_INSTALL), listOf("PREPARED")))
    }

    @Test fun `late requests cannot overwrite a newer source choice`() {
        val selected = ManagedApp("org.example.app", "Example", setOf("a"), source = ManagedSource.WY_STORE, sourceRevision = 7)
        assertFalse(SourceRequestPolicy.isCurrent(selected, ManagedSource.RUSTORE, 6))
        assertFalse(SourceRequestPolicy.isCurrent(selected, ManagedSource.RUSTORE, 7))
        assertTrue(SourceRequestPolicy.isCurrent(selected, ManagedSource.WY_STORE, 7))
        assertTrue(SourceRequestPolicy.isCurrent(null, ManagedSource.WY_STORE, 0))
        assertFalse(SourceRequestPolicy.isCurrent(null, ManagedSource.WY_STORE, 7))
    }

    @Test fun `replacement staging is bound to the current revision without changing preference`() {
        val selected = ManagedApp("org.example.app", "Example", setOf("a"), source = ManagedSource.RUSTORE, sourceRevision = 4)
        assertTrue(SourceRequestPolicy.isCurrent(selected, ManagedSource.WY_STORE, 4, replacementCandidate = true))
        assertFalse(SourceRequestPolicy.isCurrent(selected, ManagedSource.WY_STORE, 3, replacementCandidate = true))
        assertEquals(ManagedSource.RUSTORE, selected.source)
    }
}
