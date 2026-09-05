package dev.wystore.localization

import android.content.Context
import androidx.annotation.StringRes
import dev.wystore.R
import dev.wystore.root.RootFailure
import dev.wystore.root.RootResult

/**
 * Renders a root failure.
 *
 * A [RootResult] carries two different things: the shell's own output, which is technical and
 * already English, and a [RootFailure] for the cases Wy Store decided itself. Only the second can
 * be translated, and it is preferred when present.
 */
object RootTextResolver {

    @StringRes
    fun stringRes(failure: RootFailure): Int = when (failure) {
        RootFailure.NO_ARTIFACTS -> R.string.root_error_no_artifacts
        RootFailure.ARTIFACT_MISSING -> R.string.root_error_artifact_missing
        RootFailure.SESSION_NOT_CREATED -> R.string.root_error_session
        RootFailure.WRITE_FAILED -> R.string.root_error_write
        RootFailure.INVALID_PACKAGE_NAME -> R.string.root_error_invalid_package
        RootFailure.TIMED_OUT -> R.string.root_error_timeout
        RootFailure.ROOT_UNAVAILABLE -> R.string.root_error_unavailable
    }

    fun describe(context: Context, result: RootResult): String =
        result.failure?.let { context.getString(stringRes(it)) }
            ?: result.output.trim().ifBlank { context.getString(R.string.root_error_unavailable) }
}
