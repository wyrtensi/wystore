package dev.wystore.data

import android.content.Context

/**
 * Which apps the source has already said it does not carry.
 *
 * A 404 is a settled answer: it will read the same on every check for as long as the app is
 * installed, so repeating it in the summary turns a real finding into a line the user learns to
 * scroll past. Saying it once is the point of saying it at all.
 *
 * Not the same as giving up on the app. It is still looked up on every round - catalogues gain
 * apps - and the moment the source answers for it, this is forgotten, so if it disappears again
 * later that is news again.
 */
object MissingFromSourcePolicy {

    /** The ones worth reporting: those the user has not already been told about. */
    fun newlyMissing(known: Set<String>, missingNow: Set<String>): Set<String> = missingNow - known

    /**
     * What to remember after a round. An app the source answered for is dropped from the list
     * whether it was found or not - the source clearly knows it now.
     */
    fun remember(
        known: Set<String>,
        missingNow: Set<String>,
        answeredNow: Set<String>
    ): Set<String> = (known + missingNow) - answeredNow
}

/** Where [MissingFromSourcePolicy] keeps its answer between checks. */
class MissingFromSourceStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("wystore_checks", Context.MODE_PRIVATE)

    fun read(): Set<String> = prefs.getStringSet(KEY, emptySet())?.toSet().orEmpty()

    fun write(packages: Set<String>) {
        // Committed rather than applied: a worker's process can end the moment it returns.
        prefs.edit().putStringSet(KEY, packages).commit()
    }

    private companion object {
        const val KEY = "missing_from_source"
    }
}
