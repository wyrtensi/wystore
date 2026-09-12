package dev.wystore.updates

import android.content.Context
import dev.wystore.data.VerificationError
import dev.wystore.updates.model.QueueErrorCode

/**
 * The one verification failure the person in front of the phone can answer for.
 *
 * RuStore publishes the signing fingerprint it expects of an app, and Wy Store refuses a file whose
 * signature is not the one advertised. Usually the two agree; when they do not, the store is
 * contradicting itself, and from the outside there is no telling stale catalogue data from a file
 * that is not what it claims to be. That is a decision, not a diagnosis - so it is offered to the
 * user instead of being taken silently either way.
 *
 * What the answer waives is exactly this one comparison: the package name still has to match, an
 * update still has to keep the signature of what is installed, the download still has to match the
 * hash the source gave for it, and Android refuses it otherwise.
 */
object UnverifiedSourceConsent {

    private const val SEPARATOR = "|"

    fun isAnswerable(errorCode: QueueErrorCode?, detail: String?): Boolean =
        errorCode == QueueErrorCode.SIGNATURE &&
            detail == VerificationError.SOURCE_FINGERPRINT_MISMATCH.name

    /** What a refusal is: the fingerprint the source advertised, and the one the file carries. */
    fun record(advertisedDigest: String?, archiveDigest: String?): String? {
        val advertised = advertisedDigest?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
        val archive = archiveDigest?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
        return "$advertised$SEPARATOR$archive"
    }

    /**
     * The signature an accepted [record] allows, when it was accepted against the fingerprint the
     * source is advertising now. A source that has since changed its claim matches nothing, and
     * the file is refused again rather than let through on an answer about something else.
     */
    fun acceptedArchiveDigest(record: String?, advertisedDigest: String?): String? {
        val advertised = advertisedDigest?.lowercase() ?: return null
        val parts = record?.split(SEPARATOR) ?: return null
        if (parts.size != 2 || parts[0] != advertised) return null
        return parts[1].takeIf { it.isNotBlank() }
    }
}

/**
 * Whether removing the installed app is worth offering for a failure.
 *
 * The queue offered "uninstall and install" for every signature failure there is, including on an
 * app that is not on the phone at all - there was nothing to uninstall, and the button said so to
 * nobody's benefit. It helps in exactly one situation: something is installed, and it is what the
 * new file cannot go over.
 *
 * It lives beside [UnverifiedSourceConsent] because both answer the same pair, and the two answers
 * are mutually exclusive: a file the source would not vouch for is a question about the file, and
 * removing an app the phone may not even have does not answer it.
 */
object ReplaceOfferPolicy {

    /** The refusals an uninstall actually clears; the other eight survive it unchanged. */
    private val CLEARED_BY_REINSTALL = setOf(
        VerificationError.SIGNATURE_MISMATCH,
        VerificationError.WRONG_PACKAGE_FOR_UPDATE
    )

    fun offersReplace(
        errorCode: QueueErrorCode?,
        detail: String?,
        appInstalled: Boolean
    ): Boolean {
        if (!appInstalled || errorCode != QueueErrorCode.SIGNATURE) return false
        // A row that does not name which check refused it cannot be shown to be one an uninstall
        // clears, and is not offered the one action that costs the app's data.
        val error = detail?.let { name -> VerificationError.entries.firstOrNull { it.name == name } }
            ?: return false
        return error in CLEARED_BY_REINSTALL
    }
}

/**
 * What was refused, and what the user said about it.
 *
 * Consent names the two fingerprints the user was shown - the one the source advertises and the one
 * the file actually carries - and not the app's version. A queue row for an app that is not
 * installed yet carries no version at all (there is nothing to read one from until the download
 * starts), so a version-shaped key would have read the same for every future first install of that
 * package: a waiver given once would silently cover a different build later, in the very case where
 * the advertised fingerprint is the only cross-check there is.
 *
 * Tied to the pair instead, the answer covers the file it was given for. RuStore correcting its
 * catalogue, or serving something signed by anyone else, no longer matches, and the question is
 * asked again.
 */
class UnverifiedSourceStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("wystore_queue", Context.MODE_PRIVATE)

    /** Written where the file is refused, so the answer has something to be about. */
    fun rememberRefusal(packageName: String, advertisedDigest: String?, archiveDigests: Set<String>) {
        val record = UnverifiedSourceConsent.record(advertisedDigest, archiveDigests.firstOrNull())
            ?: return
        // Committed rather than applied: a worker's process can end the moment it returns.
        prefs.edit().putString(PENDING + packageName, record).commit()
    }

    /** The user's answer to the refusal last recorded for this package. */
    fun accept(packageName: String): Boolean {
        val pending = prefs.getString(PENDING + packageName, null) ?: return false
        prefs.edit().putString(ACCEPTED + packageName, pending).commit()
        return true
    }

    /**
     * The same answer given before anything was downloaded.
     *
     * An app that is already installed can be compared against the catalogue without fetching a
     * thing, so the disagreement is visible - and answerable - on its page. What is accepted there
     * is the signature the phone already carries: if the file the source serves turns out to carry
     * another one, it is refused again and the question comes back, this time about the file.
     */
    fun accept(packageName: String, advertisedDigest: String?, archiveDigest: String?): Boolean {
        val record = UnverifiedSourceConsent.record(advertisedDigest, archiveDigest) ?: return false
        prefs.edit().putString(ACCEPTED + packageName, record).commit()
        return true
    }

    /**
     * The signature the user accepted for this package against this advertised fingerprint, or null
     * when they have accepted nothing that applies to what the source is claiming now.
     */
    fun acceptedArchiveDigest(packageName: String, advertisedDigest: String?): String? =
        UnverifiedSourceConsent.acceptedArchiveDigest(
            record = prefs.getString(ACCEPTED + packageName, null),
            advertisedDigest = advertisedDigest
        )

    fun clear(packageName: String) {
        prefs.edit().remove(PENDING + packageName).remove(ACCEPTED + packageName).commit()
    }

    private companion object {
        const val PENDING = "unverified_source_refused:"
        const val ACCEPTED = "unverified_source_accepted:"
    }
}
