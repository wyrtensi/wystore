package dev.wystore.ui.updates

/**
 * What the panel at the top of Updates says, decided apart from the composable.
 *
 * With no accepted apps the panel used to read "All apps are up to date" with a full 1/1 ring,
 * and the empty state under it said "No accepted apps yet": two answers to one question. With
 * nothing to look after there is nothing that is up to date, so the panel steps aside and the
 * empty state is the only voice on the screen.
 */
object UpdatesHeroPolicy {

    enum class Headline { READY, WAITING, PROBLEMS, CHECKING, UP_TO_DATE }

    /**
     * The panel's headline, or null when the panel should not be shown at all.
     *
     * [tracked] is the accepted apps; [checking] is a check that is running now, which still gets
     * its panel because it is the only place that says how far the check has got.
     */
    fun headline(
        tracked: Int,
        ready: Int,
        waiting: Int,
        problems: Int,
        checking: Boolean
    ): Headline? = when {
        ready > 0 -> Headline.READY
        waiting > 0 -> Headline.WAITING
        checking -> Headline.CHECKING
        tracked == 0 -> null
        problems > 0 -> Headline.PROBLEMS
        else -> Headline.UP_TO_DATE
    }

    /** The empty state is shown exactly when there is nothing to list and the panel is gone. */
    fun showsEmptyState(tracked: Int, pending: Int, waiting: Int): Boolean =
        tracked == 0 && pending == 0 && waiting == 0
}
