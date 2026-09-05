package dev.wystore.selfupdate

/**
 * Compares Wy Store's own version against a GitHub release tag.
 *
 * A release id cannot be used for this the way it is for other GitHub apps: the app has to know
 * whether the tag it found is genuinely newer than the build that is running, and the only thing
 * both sides agree on is the version string. Kept free of Android types so the ordering rules are
 * directly testable.
 */
object SelfUpdateVersion {

    /**
     * Strips the decoration a tag may carry: a leading "v", and any pre-release or build suffix
     * after the numeric part ("v1.2.3-rc1" and "1.2.3+build7" both parse as 1.2.3).
     */
    fun parse(raw: String?): List<Int>? {
        val text = raw?.trim()?.removePrefix("v")?.removePrefix("V").orEmpty()
        if (text.isEmpty()) return null
        val numeric = text.takeWhile { it.isDigit() || it == '.' }.trim('.')
        if (numeric.isEmpty()) return null
        val parts = numeric.split('.').map { it.toIntOrNull() ?: return null }
        return parts.takeIf { it.isNotEmpty() }
    }

    /**
     * Standard component-wise ordering, with missing components read as zero so "1.2" and "1.2.0"
     * compare equal rather than the shorter one losing.
     */
    fun compare(left: List<Int>, right: List<Int>): Int {
        val size = maxOf(left.size, right.size)
        for (index in 0 until size) {
            val a = left.getOrElse(index) { 0 }
            val b = right.getOrElse(index) { 0 }
            if (a != b) return a.compareTo(b)
        }
        return 0
    }

    /**
     * Whether [candidateTag] is worth offering to someone running [currentVersionName].
     *
     * An unparseable tag is never offered: shipping an update the app cannot reason about is worse
     * than staying on the current build, and a repository can carry tags that are not releases.
     */
    fun isNewer(candidateTag: String?, currentVersionName: String): Boolean {
        val candidate = parse(candidateTag) ?: return false
        val current = parse(currentVersionName) ?: return false
        return compare(candidate, current) > 0
    }
}
