package dev.wystore.localization

import android.content.Context
import androidx.annotation.StringRes
import dev.wystore.R
import dev.wystore.data.SourceError
import dev.wystore.data.SourceFormatException

/**
 * Renders a [SourceError] in the user's language.
 *
 * The sources run where there are no resources — inside workers, receivers and repositories — so
 * they report a code. This is the single place that turns one into text; before it existed, a
 * Russian sentence thrown from the data layer was displayed verbatim on an English device.
 */
object SourceTextResolver {

    @StringRes
    fun stringRes(error: SourceError): Int = when (error) {
        SourceError.GITHUB_REPOSITORY_NOT_FOUND -> R.string.source_error_github_not_found
        SourceError.GITHUB_RATE_LIMITED -> R.string.source_error_github_rate_limited
        SourceError.GITHUB_UNAVAILABLE -> R.string.source_error_github_unavailable
        SourceError.GITHUB_INVALID_URL -> R.string.source_error_github_invalid_url
        SourceError.GITHUB_NO_DOWNLOAD_LOCATION -> R.string.source_error_github_no_location
        SourceError.GITHUB_TOO_MANY_REDIRECTS -> R.string.source_error_github_redirects
        SourceError.RUSTORE_UNAVAILABLE -> R.string.source_error_rustore_unavailable
        SourceError.RUSTORE_EMPTY_RESPONSE -> R.string.source_error_rustore_empty
        SourceError.RUSTORE_API_REJECTED -> R.string.source_error_rustore_api
        SourceError.RUSTORE_NO_DOWNLOAD_LINK -> R.string.source_error_no_download_link
        SourceError.RUSTORE_BUNDLE_INVALID -> R.string.source_error_bundle_invalid
        SourceError.FORMAT_CHANGED -> R.string.source_error_format_changed
        SourceError.WRONG_PACKAGE -> R.string.source_error_wrong_package
        SourceError.INVALID_PACKAGE_NAME -> R.string.source_error_invalid_package
        SourceError.INVALID_SECTION -> R.string.source_error_invalid_section
        SourceError.EMPTY_SECTION -> R.string.source_error_empty_section
        SourceError.INCOMPATIBLE_ANDROID -> R.string.source_error_incompatible_android
        SourceError.UNTRUSTED_HOST -> R.string.source_error_untrusted_host
        SourceError.INVALID_ARTIFACT_URL -> R.string.source_error_invalid_artifact_url
        SourceError.ARTIFACT_TOO_LARGE -> R.string.source_error_artifact_too_large
        SourceError.ARTIFACT_SIZE_MISMATCH -> R.string.source_error_size_mismatch
        SourceError.ARTIFACT_INTEGRITY_MISMATCH -> R.string.source_error_integrity
        SourceError.ARTIFACT_WRITE_FAILED -> R.string.source_error_write_failed
        SourceError.DOWNLOAD_FAILED -> R.string.source_error_download_failed
    }

    fun resolve(context: Context, error: SourceError): String = context.getString(stringRes(error))

    /**
     * Text for any failure that reached the UI.
     *
     * A typed source failure is rendered from resources; anything else falls back to its own
     * message, which is the best available answer for an exception the app did not raise itself.
     */
    fun describe(context: Context, throwable: Throwable?): String? = when {
        throwable == null -> null
        throwable is SourceFormatException -> resolve(context, throwable.error)
        else -> throwable.message
    }
}
