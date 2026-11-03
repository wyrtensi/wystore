package dev.wystore.data

enum class ReservedSourceTarget { SOURCES, ABOUT }

data class ReservedSourcePrompt(val target: ReservedSourceTarget, val backupFailed: Boolean) {
    companion object {
        fun from(error: SourceError, backupFailed: Boolean): ReservedSourcePrompt? = when (error) {
            SourceError.GITHUB_RESERVED_LIBRARY -> ReservedSourcePrompt(ReservedSourceTarget.SOURCES, backupFailed)
            SourceError.GITHUB_RESERVED_SELF -> ReservedSourcePrompt(ReservedSourceTarget.ABOUT, backupFailed)
            else -> null
        }
    }
}
