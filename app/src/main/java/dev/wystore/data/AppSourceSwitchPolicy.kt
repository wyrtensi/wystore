package dev.wystore.data

import dev.wystore.updates.model.QueueState

sealed interface AppSourceChoice {
    val packageName: String?
    val source: ManagedSource
    data class RuStore(override val packageName: String) : AppSourceChoice { override val source = ManagedSource.RUSTORE }
    data class Library(override val packageName: String) : AppSourceChoice { override val source = ManagedSource.WY_STORE }
    data class GitHub(val repository: GitHubRepository, val knownPackage: String?) : AppSourceChoice {
        override val packageName get() = knownPackage
        override val source = ManagedSource.GITHUB
    }
}
enum class SourceSwitchDecision { SAVE_CHOICE, VERIFY_FOR_REPLACEMENT, DEFER_UNTIL_INSTALL_SETTLES, INVALID }

object AppSourceSwitchPolicy {
    fun decide(installed: InstalledApp, candidate: AppSourceChoice, signature: SignatureCompatibility,
               committedInstall: Boolean): SourceSwitchDecision = when {
        candidate.packageName != installed.packageName -> SourceSwitchDecision.INVALID
        committedInstall -> SourceSwitchDecision.DEFER_UNTIL_INSTALL_SETTLES
        signature == SignatureCompatibility.MISMATCH -> SourceSwitchDecision.VERIFY_FOR_REPLACEMENT
        else -> SourceSwitchDecision.SAVE_CHOICE
    }
}

object SourceRequestPolicy {
    fun preserveSelection(current: ManagedApp?, replacement: ManagedApp): ManagedApp =
        if (current != null && replacement.sourceRevision < current.sourceRevision) replacement.copy(
            source = current.source, sourceRevision = current.sourceRevision,
            githubRepository = current.githubRepository, githubReleaseId = current.githubReleaseId,
            knownGithubRepository = current.knownGithubRepository ?: replacement.knownGithubRepository
        ) else replacement

    fun isCurrent(selected: ManagedApp?, source: ManagedSource, revision: Long,
                  replacementCandidate: Boolean = false,
                  lastKnownRevision: Long = selected?.sourceRevision ?: 0): Boolean {
        if (revision != lastKnownRevision) return false
        if (selected == null) return true
        if (revision != selected.sourceRevision) return false
        // Revision zero predates explicit source switching. Preserve existing manual GitHub/adoption requests.
        return revision == 0L || selected.source == source || replacementCandidate
    }

    fun blocksSwitch(states: List<QueueState>, sessions: List<String>): Boolean =
        QueueState.INSTALLING in states || sessions.any { it != "PREPARED" }
}
