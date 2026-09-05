package dev.wystore.localization

import android.content.Context
import androidx.annotation.StringRes
import dev.wystore.R
import dev.wystore.data.VerificationError

/**
 * Renders a [VerificationError] in the user's language.
 *
 * `SigningVerifier` runs where there are no resources — inside a worker, a receiver and the
 * installer — so it reports a code. This is the single place that turns one into text, which is why
 * a failed install no longer shows Russian on an English device.
 */
object VerificationTextResolver {

    @StringRes
    fun stringRes(error: VerificationError): Int = when (error) {
        VerificationError.NOT_AN_APK -> R.string.verify_not_an_apk
        VerificationError.NO_BASE_APK -> R.string.verify_no_base_apk
        VerificationError.MIXED_VERSIONS -> R.string.verify_mixed_versions
        VerificationError.WRONG_PACKAGE -> R.string.verify_wrong_package
        VerificationError.UNREADABLE_SIGNATURE -> R.string.verify_unreadable_signature
        VerificationError.MIXED_SIGNATURES -> R.string.verify_mixed_signatures
        VerificationError.SOURCE_FINGERPRINT_MISMATCH -> R.string.verify_source_fingerprint
        VerificationError.WRONG_PACKAGE_FOR_UPDATE -> R.string.verify_wrong_package_update
        VerificationError.DOWNGRADE -> R.string.verify_downgrade
        VerificationError.SIGNATURE_MISMATCH -> R.string.verify_signature_mismatch
    }

    fun resolve(context: Context, error: VerificationError): String =
        context.getString(stringRes(error))
}
