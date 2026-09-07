package dev.wystore.ui.components

/**
 * The part of a version string a person reads.
 *
 * Publishers append build metadata to `versionName` - "2026.08.4 #162.1gpr" - which says nothing
 * in a list and takes the width the source badge next to it needs. The tail is dropped in rows;
 * the app page still shows the version exactly as the package declares it.
 */
fun shortVersionName(raw: String?): String? {
    val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val head = trimmed.substringBefore(' ').substringBefore('#').trim()
    return head.takeIf { it.isNotEmpty() } ?: trimmed
}
