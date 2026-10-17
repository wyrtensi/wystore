package dev.wystore.data

/**
 * The sections the GitHub catalogue is sorted into.
 *
 * Entries used to carry Russian words ("Обход блокировок"), so every other interface language showed
 * them in Russian. They carry slugs now and the label comes from the app's strings (see
 * CategoryLabels). Search runs in the data layer, without the app's strings, so each section also
 * lists the words people type for it in Russian and English.
 */
object GitHubCategories {
    const val VPN = "vpn"
    const val PROXY = "proxy"
    const val UNBLOCK = "unblock"
    const val NETWORK = "network"
    const val PRIVACY = "privacy"
    const val MESSENGERS = "messengers"
    const val MEDIA = "media"
    const val MUSIC = "music"
    const val BROWSERS = "browsers"
    const val STORES = "stores"
    const val FILES = "files"
    const val BACKUP = "backup"
    const val PHOTOS = "photos"
    const val SECURITY = "security"
    const val UTILITIES = "utilities"
    const val KEYBOARDS = "keyboards"
    const val TORRENTS = "torrents"
    const val READING = "reading"
    const val WEATHER = "weather"
    const val LEARNING = "learning"

    private val searchWords: Map<String, List<String>> = mapOf(
        VPN to listOf("vpn", "впн"),
        PROXY to listOf("proxy", "прокси"),
        UNBLOCK to listOf("обход блокировок", "блокировк", "dpi", "unblock", "censorship"),
        NETWORK to listOf("сеть", "network"),
        PRIVACY to listOf("приватность", "privacy", "tor"),
        MESSENGERS to listOf("мессенджер", "messenger", "чат", "chat"),
        MEDIA to listOf("видео", "video", "youtube", "медиа", "media"),
        MUSIC to listOf("музыка", "music"),
        BROWSERS to listOf("браузер", "browser"),
        STORES to listOf("магазин", "store"),
        FILES to listOf("файл", "file"),
        BACKUP to listOf("бэкап", "резерв", "backup"),
        PHOTOS to listOf("фото", "photo"),
        SECURITY to listOf("безопасность", "security", "пароль", "password", "2fa"),
        UTILITIES to listOf("инструмент", "утилит", "utilit", "tool"),
        KEYBOARDS to listOf("клавиатур", "keyboard"),
        TORRENTS to listOf("торрент", "torrent"),
        READING to listOf("чтение", "книг", "манга", "reading", "book", "manga"),
        WEATHER to listOf("погода", "weather"),
        LEARNING to listOf("учёба", "учеба", "обучение", "learning", "study")
    )

    /** Whether [needle], already lower-cased, names section [slug] in any of its words. */
    fun matches(slug: String, needle: String): Boolean =
        slug.contains(needle) || searchWords[slug].orEmpty().any { it.contains(needle) }
}
