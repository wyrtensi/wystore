package dev.wystore.data

/**
 * A GitHub repository presented as a store entry.
 *
 * [curated] marks the entries Wy Store ships with. Repositories the user adds themselves appear
 * here too, so both kinds are searchable and open the same store-style page.
 */
data class GitHubCatalogEntry(
    val repository: GitHubRepository,
    val title: String,
    val publisher: String,
    val summary: String,
    val categories: List<String> = emptyList(),
    val curated: Boolean = false,
    /**
     * The project's own icon, when it ships one. Must be a GitHub-hosted URL; anything else is
     * ignored by [GitHubUrlPolicy] and the owner avatar is used instead.
     */
    val iconOverrideUrl: String? = null,
    /**
     * Restricts which `.apk` assets of a release are offered, for repositories that attach APKs
     * belonging to something else. sing-box, for example, ships ~40 OpenWrt/Alpine `.apk` packages
     * alongside its Android build.
     */
    val assetNamePattern: String? = null
) {
    fun assetPattern(): Regex? = assetNamePattern?.let { Regex(it, RegexOption.IGNORE_CASE) }

    val slug: String get() = repository.displayName

    /**
     * Icon for a catalogue card, available without calling the API. Falls back to the owner avatar,
     * which GitHub serves from `github.com/<owner>.png` as a redirect the image loader follows.
     */
    val iconUrl: String
        get() = iconOverrideUrl?.takeIf { GitHubUrlPolicy.isTrustedImage(it) }
            ?: "https://github.com/${repository.owner}.png?size=200"
}

/**
 * Live metadata read from the GitHub API, so a curated entry does not go stale as the project moves.
 */
data class GitHubRepositoryInfo(
    val repository: GitHubRepository,
    val description: String?,
    val stars: Int?,
    val homepage: String?,
    val avatarUrl: String?,
    val topics: List<String> = emptyList(),
    val license: String? = null,
    val archived: Boolean = false,
    val screenshots: List<String> = emptyList()
)

object GitHubCatalog {

    /**
     * Repositories Wy Store ships with, so GitHub is browsable out of the box rather than only
     * after the user pastes a link.
     */
    val CURATED: List<GitHubCatalogEntry> = listOf(
        GitHubCatalogEntry(
            repository = GitHubRepository("amnezia-vpn", "amnezia-client"),
            title = "AmneziaVPN",
            publisher = "amnezia-vpn",
            summary = "Клиент AmneziaVPN: свой VPN-сервер с протоколами AmneziaWG, WireGuard, OpenVPN и XRay.",
            categories = listOf("VPN", "Сеть"),
            curated = true
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("romanvht", "ByeDPIAndroid"),
            title = "ByeByeDPI",
            publisher = "romanvht",
            summary = "Локальный обход блокировок по DPI без VPN-сервера и подписки: трафик идёт " +
                "через прокси на самом телефоне.",
            categories = listOf("Обход блокировок", "Сеть"),
            curated = true
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("2dust", "v2rayNG"),
            title = "v2rayNG",
            publisher = "2dust",
            summary = "Клиент VLESS, VMess, Reality и XRay — формат конфигов, который выдают " +
                "большинство прокси-сервисов.",
            categories = listOf("VPN", "Прокси"),
            curated = true
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("amnezia-vpn", "amneziawg-android"),
            title = "AmneziaWG",
            publisher = "amnezia-vpn",
            summary = "Отдельный клиент AmneziaWG — лёгкая версия для готового конфига, без " +
                "полного клиента AmneziaVPN.",
            categories = listOf("VPN"),
            curated = true
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("SagerNet", "sing-box"),
            title = "sing-box",
            publisher = "SagerNet",
            summary = "Универсальная прокси-платформа: движок, на котором работает большинство " +
                "современных клиентов.",
            categories = listOf("VPN", "Прокси"),
            curated = true,
            // Releases also carry ~40 OpenWrt/Alpine .apk packages; only the Android build is ours.
            assetNamePattern = "^SFA-"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("signalapp", "Signal-Android"),
            title = "Signal",
            publisher = "signalapp",
            summary = "Мессенджер со сквозным шифрованием. В российских магазинах приложений его " +
                "нет — официальная сборка публикуется только здесь.",
            categories = listOf("Мессенджеры"),
            curated = true
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("yuliskov", "SmartTube"),
            title = "SmartTube",
            publisher = "yuliskov",
            summary = "YouTube без рекламы для Android TV и ТВ-приставок.",
            categories = listOf("Медиа"),
            curated = true,
            assetNamePattern = "^SmartTube_stable"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("ImranR98", "Obtainium"),
            title = "Obtainium",
            publisher = "ImranR98",
            summary = "Устанавливает и обновляет приложения напрямую из релизов GitHub, GitLab и " +
                "F-Droid.",
            categories = listOf("Магазины"),
            curated = true
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("localsend", "localsend"),
            title = "LocalSend",
            publisher = "localsend",
            summary = "Передача файлов между устройствами по локальной сети — без облака и без " +
                "регистрации.",
            categories = listOf("Файлы"),
            curated = true,
            assetNamePattern = "android"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("hiddify", "hiddify-app"),
            title = "Hiddify",
            publisher = "hiddify",
            summary = "Автоматический подбор протокола: sing-box, XRay, Hysteria, TUIC и Reality " +
                "в одном клиенте с простым интерфейсом.",
            categories = listOf("VPN", "Прокси"),
            curated = true,
            assetNamePattern = "android"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("MatsuriDayo", "NekoBoxForAndroid"),
            title = "NekoBox",
            publisher = "MatsuriDayo",
            summary = "Клиент sing-box с подписками и правилами маршрутизации.",
            categories = listOf("VPN", "Прокси"),
            curated = true
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("guardianproject", "orbot"),
            title = "Orbot",
            publisher = "guardianproject",
            summary = "Tor для Android со сменными транспортами (obfs4, snowflake), которые " +
                "переживают блокировки.",
            categories = listOf("VPN", "Приватность"),
            curated = true
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("Gedsh", "InviZible"),
            title = "InviZible Pro",
            publisher = "Gedsh",
            summary = "Tor, DNSCrypt, I2P и файрвол в одном приложении.",
            categories = listOf("VPN", "Приватность"),
            curated = true
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("ProtonVPN", "android-app"),
            title = "Proton VPN",
            publisher = "ProtonVPN",
            summary = "VPN без логов с бесплатным тарифом и собственными транспортами против " +
                "блокировок.",
            categories = listOf("VPN"),
            curated = true,
            assetNamePattern = "vanilla|direct"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("simplex-chat", "simplex-chat"),
            title = "SimpleX Chat",
            publisher = "simplex-chat",
            summary = "Мессенджер вообще без идентификаторов: ни номера телефона, ни аккаунта.",
            categories = listOf("Мессенджеры", "Приватность"),
            curated = true,
            assetNamePattern = "^simplex"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("mollyim", "mollyim-android"),
            title = "Molly",
            publisher = "mollyim",
            summary = "Форк Signal с шифрованием базы данных и поддержкой прокси и Tor.",
            categories = listOf("Мессенджеры", "Приватность"),
            curated = true
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("Nekogram", "Nekogram"),
            title = "Nekogram",
            publisher = "Nekogram",
            summary = "Форк Telegram без ограничений официального клиента.",
            categories = listOf("Мессенджеры"),
            curated = true
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("uazo", "cromite"),
            title = "Cromite",
            publisher = "uazo",
            summary = "Браузер на Chromium со встроенной блокировкой рекламы и трекеров.",
            categories = listOf("Браузеры"),
            curated = true,
            assetNamePattern = "ChromePublic"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("libre-tube", "LibreTube"),
            title = "LibreTube",
            publisher = "libre-tube",
            summary = "YouTube без рекламы и без аккаунта Google через инстансы Piped.",
            categories = listOf("Медиа"),
            curated = true
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("mostafaalagamy", "Metrolist"),
            title = "Metrolist",
            publisher = "mostafaalagamy",
            summary = "Клиент YouTube Music без рекламы, с фоновым воспроизведением.",
            categories = listOf("Медиа"),
            curated = true
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("deniscerri", "ytdlnis"),
            title = "YTDLnis",
            publisher = "deniscerri",
            summary = "Графическая оболочка yt-dlp: скачивание видео и аудио для офлайна.",
            categories = listOf("Медиа"),
            curated = true
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("Droid-ify", "client"),
            title = "Droid-ify",
            publisher = "Droid-ify",
            summary = "Быстрый клиент F-Droid — доступ ко всему каталогу свободных приложений.",
            categories = listOf("Магазины"),
            curated = true
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("NeoApplications", "Neo-Store"),
            title = "Neo Store",
            publisher = "NeoApplications",
            summary = "Альтернативный клиент F-Droid с современным интерфейсом.",
            categories = listOf("Магазины"),
            curated = true
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("Catfriend1", "syncthing-android"),
            title = "Syncthing-Fork",
            publisher = "Catfriend1",
            summary = "P2P-синхронизация файлов без облака. Поддерживаемое продолжение Syncthing " +
                "для Android — официальный клиент архивирован.",
            categories = listOf("Файлы", "Бэкап"),
            curated = true
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("ente-io", "ente"),
            title = "Ente Photos",
            publisher = "ente-io",
            summary = "Резервное копирование фото со сквозным шифрованием — замена Google Фото.",
            categories = listOf("Фото", "Бэкап"),
            curated = true,
            assetNamePattern = "^ente-photos"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("stratumauth", "app"),
            title = "Stratum",
            publisher = "stratumauth",
            summary = "Офлайновый генератор кодов двухфакторной аутентификации.",
            categories = listOf("Безопасность"),
            curated = true
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("wyrtensi", "CapturePort"),
            title = "CapturePort",
            publisher = "wyrtensi",
            summary = "CapturePort — захват и передача экрана.",
            categories = listOf("Инструменты"),
            curated = true,
            iconOverrideUrl = "https://raw.githubusercontent.com/wyrtensi/CapturePort/HEAD/" +
                "android/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png"
        )
    )

    /**
     * Everything browsable: the curated list plus whatever the user added, without duplicates. A
     * user-added repository that matches a curated one keeps the curated presentation.
     */
    fun entries(userRepositories: List<GitHubRepository> = emptyList()): List<GitHubCatalogEntry> {
        val curatedBySlug = CURATED.associateBy { it.slug.lowercase() }
        val added = userRepositories
            .filter { it.displayName.lowercase() !in curatedBySlug }
            .distinctBy { it.displayName.lowercase() }
            .map { repository ->
                GitHubCatalogEntry(
                    repository = repository,
                    title = repository.name,
                    publisher = repository.owner,
                    summary = "",
                    curated = false
                )
            }
        return CURATED + added
    }

    /**
     * Matches a search query against the catalogue.
     *
     * A repository is addressed as `owner/name`, and people also search by the product name, so
     * owner, name, title, publisher, summary and category text are all matched. An exact
     * `owner/name` match sorts first, then curated entries, then the rest alphabetically.
     */
    fun search(
        query: String,
        userRepositories: List<GitHubRepository> = emptyList()
    ): List<GitHubCatalogEntry> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return emptyList()
        return entries(userRepositories)
            .filter { entry ->
                entry.slug.lowercase().contains(needle) ||
                    entry.repository.name.lowercase().contains(needle) ||
                    entry.repository.owner.lowercase().contains(needle) ||
                    entry.title.lowercase().contains(needle) ||
                    entry.publisher.lowercase().contains(needle) ||
                    entry.summary.lowercase().contains(needle) ||
                    entry.categories.any { it.lowercase().contains(needle) }
            }
            .sortedWith(
                compareByDescending<GitHubCatalogEntry> { it.slug.equals(needle, ignoreCase = true) }
                    .thenByDescending { it.title.equals(needle, ignoreCase = true) }
                    .thenByDescending { it.curated }
                    .thenBy { it.title.lowercase() }
            )
    }

    fun find(slug: String, userRepositories: List<GitHubRepository> = emptyList()): GitHubCatalogEntry? =
        entries(userRepositories).firstOrNull { it.slug.equals(slug, ignoreCase = true) }
}

/**
 * GitHub serves owner avatars from its own CDN. Icons are only rendered from hosts that actually
 * belong to GitHub, so a repository cannot point the app at arbitrary image hosts.
 */
object GitHubUrlPolicy {
    private val ALLOWED_IMAGE_HOSTS = setOf(
        "avatars.githubusercontent.com",
        "raw.githubusercontent.com",
        "github.com"
    )

    fun isTrustedImage(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return runCatching {
            val uri = java.net.URI(url)
            uri.scheme == "https" &&
                uri.host in ALLOWED_IMAGE_HOSTS &&
                uri.port in setOf(-1, 443) &&
                uri.userInfo == null
        }.getOrDefault(false)
    }
}
