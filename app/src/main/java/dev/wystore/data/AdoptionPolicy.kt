package dev.wystore.data

/** One way Wy Store could take over an installed app it does not manage yet. */
sealed interface AdoptionOption {
    /** Kept as it is: updates come from where this points, over the app already on the phone. */
    val inPlace: Boolean

    /** RuStore carries this very package and its signature does not rule out an update over it. */
    data class RuStore(val candidate: AdoptionCandidate) : AdoptionOption {
        override val inPlace get() = true
    }

    /** A repository known to publish this package; its releases are where updates come from. */
    data class GitHub(
        val repository: GitHubRepository,
        val title: String,
        val iconUrl: String? = null
    ) : AdoptionOption {
        override val inPlace get() = true
    }

    /**
     * Only by removing the app and installing [candidate]: the app came from Google, the store's
     * copy is signed with another key, or it is a different package found by name.
     */
    data class Replace(val candidate: AdoptionCandidate) : AdoptionOption {
        override val inPlace get() = false
    }
}

/**
 * Where updates for an app being adopted can come from.
 *
 * "Adopt" used to mean one thing for everything not from Google: record the app as RuStore's.
 * A manually installed APK from GitHub and a vendor store (GetApps) both came out labelled
 * "RuStore", with "Check" and "Open page" pointing at a package RuStore does not have. The source
 * is now looked up before anything is recorded, and an app with no source is not accepted at all.
 *
 * Google-installed apps keep their own rules ([GoogleAdoptionPolicy]): Google signs its builds, so
 * every way of taking one over is a replacement.
 */
object AdoptionPolicy {

    fun options(
        app: InstalledApp,
        exactMatch: StoreApp?,
        catalogEntry: GitHubCatalogEntry?,
        byName: List<StoreApp> = emptyList(),
        acceptedFingerprint: String? = null
    ): List<AdoptionOption> {
        if (app.source == InstallSource.GOOGLE_PLAY) {
            return GoogleAdoptionPolicy.candidates(app.packageName, exactMatch, byName)
                .map { AdoptionOption.Replace(it) }
        }
        val options = mutableListOf<AdoptionOption>()
        val exact = exactMatch?.takeIf { it.packageName == app.packageName }
        if (exact != null) {
            val candidate = exact.toCandidate(exact = true)
            val signature = SignatureCompatibilityPolicy.evaluate(
                installedDigests = app.signingDigests,
                declaredFingerprint = exact.signatureHint,
                acceptedFingerprint = acceptedFingerprint
            )
            // A copy signed with another key cannot be installed over this one; recording it as
            // RuStore's would only fail on the first update.
            options += if (signature == SignatureCompatibility.MISMATCH) {
                AdoptionOption.Replace(candidate)
            } else {
                AdoptionOption.RuStore(candidate)
            }
        }
        catalogEntry?.takeIf { it.packageName == app.packageName }?.let { entry ->
            options += AdoptionOption.GitHub(entry.repository, entry.title, entry.iconUrl)
        }
        if (options.isEmpty()) {
            options += byName
                .filter { it.packageName != app.packageName }
                .distinctBy { it.packageName }
                .take(GoogleAdoptionPolicy.MAX_CANDIDATES)
                .map { AdoptionOption.Replace(it.toCandidate(exact = false)) }
        }
        return options
    }

    /**
     * The option that can be taken without asking, when several apps are accepted at once.
     *
     * Only an identity, and only when it is the one answer: the same package in one place, kept in
     * place. A replacement removes the app, and two sources for the same package are a choice.
     */
    fun automatic(options: List<AdoptionOption>): AdoptionOption? =
        options.singleOrNull()?.takeIf { it.inPlace }

    private fun StoreApp.toCandidate(exact: Boolean) = AdoptionCandidate(
        packageName = packageName,
        label = name.ifBlank { packageName },
        iconUrl = iconUrl,
        exact = exact
    )
}
