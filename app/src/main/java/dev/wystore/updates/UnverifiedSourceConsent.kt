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
 * Consent is recorded for one package at one version. It is not a setting and does not carry to the
 * next version, and it waives exactly this one check: the package name still has to match, an
 * update still has to keep the signature of what is installed, and Android refuses it otherwise.
 */
object UnverifiedSourceConsent {

    fun isAnswerable(errorCode: QueueErrorCode?, detail: String?): Boolean =
        errorCode == QueueErrorCode.SIGNATURE &&
            detail == VerificationError.SOURCE_FINGERPRINT_MISMATCH.name

    fun key(packageName: String, versionCode: Long): String = "$packageName@$versionCode"
}

/** Where that consent is kept, so the download that acts on it can read it from its own process. */
class UnverifiedSourceStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("wystore_queue", Context.MODE_PRIVATE)

    fun allow(packageName: String, versionCode: Long) {
        if (packageName.isBlank()) return
        // Committed rather than applied: the download may start before an apply() has landed.
        prefs.edit().putStringSet(KEY, allowed() + UnverifiedSourceConsent.key(packageName, versionCode)).commit()
    }

    fun isAllowed(packageName: String, versionCode: Long): Boolean =
        UnverifiedSourceConsent.key(packageName, versionCode) in allowed()

    fun clear(packageName: String, versionCode: Long) {
        prefs.edit().putStringSet(KEY, allowed() - UnverifiedSourceConsent.key(packageName, versionCode)).commit()
    }

    private fun allowed(): Set<String> = prefs.getStringSet(KEY, emptySet())?.toSet().orEmpty()

    private companion object {
        const val KEY = "unverified_source_allowed"
    }
}
