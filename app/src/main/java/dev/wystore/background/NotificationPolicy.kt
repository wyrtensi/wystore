package dev.wystore.background

/**
 * What a notification is allowed to do right now.
 *
 * [post] false means nothing is shown at all — the category is switched off. [alert] false means
 * the notification is posted silently: it appears in the shade but does not make a sound, which is
 * what quiet hours and a repeat of an already-seen state deserve.
 */
data class NotificationDecision(
    val post: Boolean,
    val alert: Boolean
) {
    companion object {
        val SUPPRESSED = NotificationDecision(post = false, alert = false)
    }
}

/**
 * The rules that keep Wy Store quiet.
 *
 * Kept free of Android types so every rule below is directly testable. Three things caused the
 * spam this replaces: a bulk update posted one notification per app plus a summary, a repeated
 * background check re-announced the same set of ready updates every time it ran, and the category
 * switches in Settings were never consulted at all.
 */
object NotificationPolicy {

    /** Items listed by name inside a digest notification before it switches to "and N more". */
    const val MAX_LISTED_ITEMS = 5

    /**
     * Whether [hourOfDay] falls inside the user's quiet window.
     *
     * The window wraps midnight, which is the normal case (23:00 to 08:00). A window whose ends
     * are equal covers nothing rather than the whole day, so a mis-set pair cannot silence the app
     * permanently.
     */
    fun isQuietHour(hourOfDay: Int, enabled: Boolean, startHour: Int, endHour: Int): Boolean {
        if (!enabled) return false
        val hour = hourOfDay.coerceIn(0, 23)
        val start = startHour.coerceIn(0, 23)
        val end = endHour.coerceIn(0, 23)
        if (start == end) return false
        return if (start < end) hour in start until end else hour >= start || hour < end
    }

    /**
     * A stable fingerprint of what a notification would say.
     *
     * Order must not matter: the queue hands back rows in whatever order Room returns them, and an
     * unordered difference is not a change the user needs to hear about a second time.
     */
    fun digestOf(keys: Collection<String>): String =
        keys.filter { it.isNotBlank() }.distinct().sorted().joinToString("|")

    /**
     * Decides how to publish a digest whose content is [digest], given what was last published.
     *
     * An unchanged digest is still posted — the shade entry has to stay accurate — but it never
     * alerts again. This is what stops a four-hourly check from buzzing four times a day about the
     * same three pending updates.
     */
    fun decide(
        categoryEnabled: Boolean,
        digest: String,
        lastPublishedDigest: String?,
        quietHours: Boolean
    ): NotificationDecision {
        if (!categoryEnabled) return NotificationDecision.SUPPRESSED
        if (digest.isEmpty()) return NotificationDecision.SUPPRESSED
        val changed = digest != lastPublishedDigest
        return NotificationDecision(post = true, alert = changed && !quietHours)
    }

    /**
     * Whether a finished check is worth telling the user about.
     *
     * A background check that found nothing is the expected outcome and says nothing; a manual
     * check always reports back, because the user is waiting for an answer.
     */
    fun shouldReportCheck(
        categoryEnabled: Boolean,
        manual: Boolean,
        updatesFound: Int,
        problems: Int
    ): Boolean {
        if (manual) return true
        if (!categoryEnabled) return false
        return updatesFound > 0 || problems > 0
    }
}
