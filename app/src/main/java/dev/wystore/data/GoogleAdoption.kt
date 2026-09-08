package dev.wystore.data

/** One app the store could put in place of a Google-installed one. */
data class AdoptionCandidate(
    val packageName: String,
    val label: String,
    val iconUrl: String?,
    /** True when this is the very same package, not a guess made from the name. */
    val exact: Boolean
)

/**
 * Finds what to reinstall an app as, when Wy Store is asked to take over one that came from Google.
 *
 * Such an app cannot simply be updated: Google signs its own builds, so an APK from anywhere else
 * refuses to install over it. Taking it over means removing it and installing the same app from a
 * source Wy Store can actually update - and "the same app" has to be established rather than
 * assumed.
 *
 * The package name settles it whenever the store carries it, which for ordinary apps it usually
 * does; that is an identity, not a resemblance, and it is offered on its own. Only when the store
 * has no such package does the name become the question, and then the answer is a short list to
 * choose from rather than a decision made on the user's behalf.
 */
object GoogleAdoptionPolicy {

    const val MAX_CANDIDATES = 3

    /** How long a started handover stays valid, so an abandoned one does not fire days later. */
    const val PENDING_WINDOW_MS = 30 * 60 * 1000L

    fun candidates(
        installedPackageName: String,
        exactMatch: StoreApp?,
        byName: List<StoreApp>
    ): List<AdoptionCandidate> {
        exactMatch?.takeIf { it.packageName == installedPackageName }?.let { app ->
            return listOf(
                AdoptionCandidate(
                    packageName = app.packageName,
                    label = app.name.ifBlank { app.packageName },
                    iconUrl = app.iconUrl,
                    exact = true
                )
            )
        }
        return byName
            .distinctBy { it.packageName }
            .take(MAX_CANDIDATES)
            .map { app ->
                AdoptionCandidate(
                    packageName = app.packageName,
                    label = app.name.ifBlank { app.packageName },
                    iconUrl = app.iconUrl,
                    exact = app.packageName == installedPackageName
                )
            }
    }

    /**
     * Whether a handover recorded at [startedAt] may still act on an uninstall seen now.
     *
     * Removing the app and installing the replacement are two separate events with the user's
     * decision and Android's dialog in between, so the intent has to survive on disk - and then it
     * has to expire, or a handover the user walked away from installs something a week later.
     */
    fun isPendingFresh(startedAt: Long, now: Long, windowMs: Long = PENDING_WINDOW_MS): Boolean =
        startedAt in (now - windowMs + 1)..now
}
