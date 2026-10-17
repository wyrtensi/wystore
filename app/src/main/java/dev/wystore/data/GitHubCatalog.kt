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
    /**
     * The summary in English. The curated summaries were written in Russian only, and once the home
     * screen started showing them an English interface had Russian under every GitHub tile. A
     * repository the user added has none; its summary is the project's own description.
     */
    val summaryEn: String? = null,
    val categories: List<String> = emptyList(),
    val curated: Boolean = false,
    /**
     * What this project installs as, when it is known.
     *
     * Without it a GitHub app is only recognised as installed once Wy Store has installed it
     * itself: the link between a repository and a package lives in the managed record, and an app
     * the user got anywhere else has none. The page then offered "Install" for something already
     * on the device. Left null rather than guessed - a wrong package name would claim the wrong
     * app is installed, which is worse than saying nothing.
     */
    val packageName: String? = null,
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
     * The summary for an interface in [language] (an ISO 639 code). Russian where it is the language
     * people there commonly read; English everywhere else, since the app has no other translation.
     */
    fun summaryIn(language: String): String =
        if (language.lowercase() in RUSSIAN_READERS || summaryEn.isNullOrBlank()) summary else summaryEn

    private companion object {
        val RUSSIAN_READERS = setOf("ru", "be", "kk", "uz")
    }

    /**
     * Whether this project is on the phone: by the package the entry states, or by what Wy Store
     * installed from this repository itself - the same two ways the app page recognises it.
     */
    fun isInstalled(installed: Collection<String>, managed: List<ManagedApp>): Boolean =
        (packageName != null && packageName in installed) ||
            managed.any { it.githubRepository == repository && it.packageName in installed }

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
            summaryEn = "AmneziaVPN client: your own VPN server with AmneziaWG, WireGuard, " +
                "OpenVPN and XRay.",
            categories = listOf(GitHubCategories.VPN, GitHubCategories.NETWORK),
            curated = true,
            packageName = "org.amnezia.vpn"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("romanvht", "ByeDPIAndroid"),
            title = "ByeByeDPI",
            publisher = "romanvht",
            summary = "Локальный обход блокировок по DPI без VPN-сервера и подписки: трафик идёт через прокси на самом телефоне.",
            summaryEn = "Local DPI bypass with no VPN server or subscription: traffic goes " +
                "through a proxy on the phone itself.",
            categories = listOf(GitHubCategories.UNBLOCK, GitHubCategories.NETWORK),
            curated = true,
            packageName = "io.github.romanvht.byedpi"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("2dust", "v2rayNG"),
            title = "v2rayNG",
            publisher = "2dust",
            summary = "Клиент VLESS, VMess, Reality и XRay — формат конфигов, который выдают большинство прокси-сервисов.",
            summaryEn = "VLESS, VMess, Reality and XRay client — the config format most proxy " +
                "services hand out.",
            categories = listOf(GitHubCategories.VPN, GitHubCategories.PROXY),
            curated = true,
            packageName = "com.v2ray.ang"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("hiddify", "hiddify-app"),
            title = "Hiddify",
            publisher = "hiddify",
            summary = "Автоматический подбор протокола: sing-box, XRay, Hysteria, TUIC и Reality в одном клиенте с простым интерфейсом.",
            summaryEn = "Picks the protocol for you: sing-box, XRay, Hysteria, TUIC and Reality " +
                "in one client with a simple interface.",
            categories = listOf(GitHubCategories.VPN, GitHubCategories.PROXY),
            curated = true,
            assetNamePattern = "android",
            packageName = "app.hiddify.com"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("MetaCubeX", "ClashMetaForAndroid"),
            title = "Clash Meta for Android",
            publisher = "MetaCubeX",
            summary = "Клиент Clash на ядре mihomo: подписки, правила маршрутизации, VLESS, Trojan и Hysteria. Самый распространённый клиент для подписочных VPN-сервисов.",
            summaryEn = "Clash client on the mihomo core: subscriptions, routing rules, VLESS, " +
                "Trojan and Hysteria. The most common client for subscription VPN services.",
            categories = listOf(GitHubCategories.PROXY, GitHubCategories.UNBLOCK),
            curated = true,
            // Releases carry the alpha flavor too; only the meta one is the published app.
            assetNamePattern = "-meta-",
            packageName = "com.github.metacubex.clash.meta",
            iconOverrideUrl = "https://raw.githubusercontent.com/MetaCubeX/ClashMetaForAndroid/main/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("chen08209", "FlClash"),
            title = "FlClash",
            publisher = "chen08209",
            summary = "Лёгкий клиент Clash и mihomo с подписками и правилами — для сервисов, которые отдают конфиг по ссылке.",
            summaryEn = "Light Clash and mihomo client with subscriptions and rules — for " +
                "services that give out a config link.",
            categories = listOf(GitHubCategories.PROXY, GitHubCategories.UNBLOCK),
            curated = true,
            assetNamePattern = "android",
            packageName = "com.follow.clash",
            iconOverrideUrl = "https://raw.githubusercontent.com/chen08209/FlClash/main/android/app/src/main/ic_launcher-playstore.png"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("KaringX", "karing"),
            title = "Karing",
            publisher = "KaringX",
            summary = "Прокси-клиент на sing-box: понимает подписки Clash, V2Ray и sing-box и разделяет трафик по правилам.",
            summaryEn = "Proxy client on sing-box: reads Clash, V2Ray and sing-box subscriptions " +
                "and splits traffic by rules.",
            categories = listOf(GitHubCategories.PROXY, GitHubCategories.UNBLOCK),
            curated = true,
            assetNamePattern = "_android_",
            packageName = "com.nebula.karing",
            iconOverrideUrl = "https://raw.githubusercontent.com/KaringX/karing/main/android/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("KaringX", "clashmi"),
            title = "Clash Mi",
            publisher = "KaringX",
            summary = "Клиент на ядре mihomo от авторов Karing: читает подписки Clash и разделяет трафик по правилам.",
            summaryEn = "mihomo-based client from the makers of Karing: reads Clash " +
                "subscriptions and splits traffic by rules.",
            categories = listOf(GitHubCategories.PROXY, GitHubCategories.UNBLOCK),
            curated = true,
            assetNamePattern = "_android_",
            packageName = "com.nebula.clashmi",
            iconOverrideUrl = "https://raw.githubusercontent.com/KaringX/clashmi/main/android/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("wgtunnel", "android"),
            title = "WG Tunnel",
            publisher = "wgtunnel",
            summary = "Менеджер WireGuard и AmneziaWG: сам включает туннель по сети, Wi-Fi и состоянию телефона.",
            summaryEn = "WireGuard and AmneziaWG manager: turns the tunnel on by itself " +
                "depending on the network, Wi-Fi and phone state.",
            categories = listOf(GitHubCategories.VPN, GitHubCategories.UNBLOCK),
            curated = true,
            assetNamePattern = "standalone",
            packageName = "com.zaneschepke.wireguardautotunnel",
            iconOverrideUrl = "https://raw.githubusercontent.com/wgtunnel/android/master/fastlane/metadata/android/en-US/images/icon.png"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("amnezia-vpn", "amneziawg-android"),
            title = "AmneziaWG",
            publisher = "amnezia-vpn",
            summary = "Отдельный клиент AmneziaWG — лёгкая версия для готового конфига, без полного клиента AmneziaVPN.",
            summaryEn = "Standalone AmneziaWG client — a light version for a ready config, " +
                "without the full AmneziaVPN client.",
            categories = listOf(GitHubCategories.VPN),
            curated = true
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("SagerNet", "sing-box"),
            title = "sing-box",
            publisher = "SagerNet",
            summary = "Универсальная прокси-платформа: движок, на котором работает большинство современных клиентов.",
            summaryEn = "Universal proxy platform: the engine most modern clients run on.",
            categories = listOf(GitHubCategories.VPN, GitHubCategories.PROXY),
            curated = true,
            // Releases also carry ~40 OpenWrt/Alpine .apk packages; only the Android build is ours.
            assetNamePattern = "^SFA-",
            packageName = "io.nekohasekai.sfa"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("MatsuriDayo", "NekoBoxForAndroid"),
            title = "NekoBox",
            publisher = "MatsuriDayo",
            summary = "Клиент sing-box с подписками и правилами маршрутизации.",
            summaryEn = "sing-box client with subscriptions and routing rules.",
            categories = listOf(GitHubCategories.VPN, GitHubCategories.PROXY),
            curated = true
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("xchacha20-poly1305", "husi"),
            title = "Husi",
            publisher = "xchacha20-poly1305",
            summary = "Клиент на sing-box, наследник SagerNet и NekoBox: VLESS, Hysteria2 и подписки.",
            summaryEn = "sing-box client, successor to SagerNet and NekoBox: VLESS, Hysteria2 " +
                "and subscriptions.",
            categories = listOf(GitHubCategories.PROXY, GitHubCategories.UNBLOCK),
            curated = true,
            assetNamePattern = "^husi-",
            packageName = "fr.husi",
            iconOverrideUrl = "https://raw.githubusercontent.com/xchacha20-poly1305/husi/dev/fastlane/metadata/android/en-US/images/icon.png"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("ExclaveNetwork", "Exclave"),
            title = "Exclave",
            publisher = "ExclaveNetwork",
            summary = "Продолжение SagerNet на Xray и sing-box: VLESS, Trojan, Shadowsocks и маршрутизация по приложениям.",
            summaryEn = "SagerNet continued on Xray and sing-box: VLESS, Trojan, Shadowsocks and " +
                "per-app routing.",
            categories = listOf(GitHubCategories.PROXY, GitHubCategories.UNBLOCK),
            curated = true,
            assetNamePattern = "^Exclave-",
            packageName = "com.github.dyhkwong.sagernet"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("schwabe", "ics-openvpn"),
            title = "OpenVPN for Android",
            publisher = "schwabe",
            summary = "Открытый клиент OpenVPN: подключает файлы .ovpn — для рабочих и собственных VPN-серверов.",
            summaryEn = "Open-source OpenVPN client: connects .ovpn files — for work and " +
                "self-hosted VPN servers.",
            categories = listOf(GitHubCategories.VPN),
            curated = true,
            assetNamePattern = "^ics-openvpn-",
            packageName = "de.blinkt.openvpn",
            iconOverrideUrl = "https://raw.githubusercontent.com/schwabe/ics-openvpn/master/main/src/main/res/mipmap-xxxhdpi/ic_launcher.png"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("guardianproject", "orbot"),
            title = "Orbot",
            publisher = "guardianproject",
            summary = "Tor для Android со сменными транспортами (obfs4, snowflake), которые переживают блокировки.",
            summaryEn = "Tor for Android with pluggable transports (obfs4, snowflake) that " +
                "survive blocking.",
            categories = listOf(GitHubCategories.VPN, GitHubCategories.PRIVACY),
            curated = true
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("Gedsh", "InviZible"),
            title = "InviZible Pro",
            publisher = "Gedsh",
            summary = "Tor, DNSCrypt, I2P и файрвол в одном приложении.",
            summaryEn = "Tor, DNSCrypt, I2P and a firewall in one app.",
            categories = listOf(GitHubCategories.VPN, GitHubCategories.PRIVACY),
            curated = true
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("ProtonVPN", "android-app"),
            title = "Proton VPN",
            publisher = "ProtonVPN",
            summary = "VPN без логов с бесплатным тарифом и собственными транспортами против блокировок.",
            summaryEn = "No-logs VPN with a free plan and its own transports against blocking.",
            categories = listOf(GitHubCategories.VPN),
            curated = true,
            assetNamePattern = "vanilla|direct",
            packageName = "ch.protonvpn.android"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("mullvad", "mullvadvpn-app"),
            title = "Mullvad VPN",
            publisher = "mullvad",
            summary = "VPN-сервис на WireGuard: аккаунт создаётся без почты и телефона.",
            summaryEn = "WireGuard VPN service: the account is created without an email or phone " +
                "number.",
            categories = listOf(GitHubCategories.VPN, GitHubCategories.PRIVACY),
            curated = true,
            assetNamePattern = "^MullvadVPN-",
            packageName = "net.mullvad.mullvadvpn"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("celzero", "rethink-app"),
            title = "Rethink",
            publisher = "celzero",
            summary = "Файрвол и DNS-фильтр по приложениям: режет рекламу и трекеры, умеет пускать трафик через WireGuard и прокси.",
            summaryEn = "Per-app firewall and DNS filter: cuts ads and trackers, can route " +
                "traffic through WireGuard and proxies.",
            categories = listOf(GitHubCategories.NETWORK, GitHubCategories.PRIVACY),
            curated = true,
            assetNamePattern = "^rethink-",
            packageName = "com.celzero.bravedns",
            iconOverrideUrl = "https://raw.githubusercontent.com/celzero/rethink-app/main/fastlane/metadata/android/en-US/images/icon.png"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("M66B", "NetGuard"),
            title = "NetGuard",
            publisher = "M66B",
            summary = "Файрвол без root: запрещает приложениям выход в сеть по Wi-Fi и мобильной связи и показывает, кто куда обращается.",
            summaryEn = "Firewall without root: blocks apps from the network on Wi-Fi and mobile " +
                "data and shows who connects where.",
            categories = listOf(GitHubCategories.NETWORK, GitHubCategories.SECURITY),
            curated = true,
            assetNamePattern = "^NetGuard-",
            packageName = "eu.faircode.netguard",
            iconOverrideUrl = "https://raw.githubusercontent.com/M66B/NetGuard/master/fastlane/metadata/android/en-US/images/icon.png"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("signalapp", "Signal-Android"),
            title = "Signal",
            publisher = "signalapp",
            summary = "Мессенджер со сквозным шифрованием. В российских магазинах приложений его нет — официальная сборка публикуется только здесь.",
            summaryEn = "End-to-end encrypted messenger. Russian app stores do not carry it — " +
                "the official build is published only here.",
            categories = listOf(GitHubCategories.MESSENGERS),
            curated = true
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("NextAlone", "Nagram"),
            title = "Nagram",
            publisher = "NextAlone",
            summary = "Неофициальный клиент Telegram с расширенными настройками и встроенным прокси.",
            summaryEn = "Unofficial Telegram client with extended settings and a built-in proxy.",
            categories = listOf(GitHubCategories.MESSENGERS),
            curated = true,
            assetNamePattern = "^Nagram-",
            packageName = "xyz.nextalone.nagram",
            iconOverrideUrl = "https://raw.githubusercontent.com/NextAlone/Nagram/main/TMessagesProj/src/main/res/mipmap-xxxhdpi/ic_launcher.png"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("Nekogram", "Nekogram"),
            title = "Nekogram",
            publisher = "Nekogram",
            summary = "Форк Telegram без ограничений официального клиента.",
            summaryEn = "Telegram fork without the official client's restrictions.",
            categories = listOf(GitHubCategories.MESSENGERS),
            curated = true
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("arsLan4k1390", "Cherrygram"),
            title = "Cherrygram",
            publisher = "arsLan4k1390",
            summary = "Клиент Telegram с настройкой интерфейса и жестов.",
            summaryEn = "Telegram client with interface and gesture settings.",
            categories = listOf(GitHubCategories.MESSENGERS),
            curated = true,
            assetNamePattern = "^Cherrygram-",
            packageName = "uz.unnarsx.cherrygram"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("forkgram", "TelegramAndroid"),
            title = "Forkgram",
            publisher = "forkgram",
            summary = "Клиент Telegram с дополнительными функциями, выходит вслед за официальными версиями.",
            summaryEn = "Telegram client with extra features, released right after the official " +
                "versions.",
            categories = listOf(GitHubCategories.MESSENGERS),
            curated = true,
            assetNamePattern = "^ForkClient",
            iconOverrideUrl = "https://raw.githubusercontent.com/forkgram/TelegramAndroid/dev/TMessagesProj/src/main/res/mipmap-xxxhdpi/ic_launcher.png"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("simplex-chat", "simplex-chat"),
            title = "SimpleX Chat",
            publisher = "simplex-chat",
            summary = "Мессенджер вообще без идентификаторов: ни номера телефона, ни аккаунта.",
            summaryEn = "Messenger with no identifiers at all: no phone number, no account.",
            categories = listOf(GitHubCategories.MESSENGERS, GitHubCategories.PRIVACY),
            curated = true,
            assetNamePattern = "^simplex",
            packageName = "chat.simplex.app"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("mollyim", "mollyim-android"),
            title = "Molly",
            publisher = "mollyim",
            summary = "Форк Signal с шифрованием базы данных и поддержкой прокси и Tor.",
            summaryEn = "Signal fork with an encrypted database and proxy and Tor support.",
            categories = listOf(GitHubCategories.MESSENGERS, GitHubCategories.PRIVACY),
            curated = true
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("session-foundation", "session-android"),
            title = "Session",
            publisher = "session-foundation",
            summary = "Мессенджер без номера телефона: аккаунт анонимный, сообщения идут через луковую маршрутизацию.",
            summaryEn = "Messenger without a phone number: the account is anonymous and messages " +
                "go through onion routing.",
            categories = listOf(GitHubCategories.MESSENGERS, GitHubCategories.PRIVACY),
            curated = true,
            assetNamePattern = "-play-release",
            packageName = "network.loki.messenger",
            iconOverrideUrl = "https://raw.githubusercontent.com/session-foundation/session-android/dev/app/src/main/ic_launcher-playstore.png"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("element-hq", "element-x-android"),
            title = "Element X",
            publisher = "element-hq",
            summary = "Клиент Matrix — децентрализованного мессенджера на своих и публичных серверах.",
            summaryEn = "Client for Matrix, a decentralised messenger on your own or public " +
                "servers.",
            categories = listOf(GitHubCategories.MESSENGERS),
            curated = true,
            assetNamePattern = "^app-fdroid-",
            packageName = "io.element.android.x",
            iconOverrideUrl = "https://raw.githubusercontent.com/element-hq/element-x-android/develop/fastlane/metadata/android/en-US/images/icon.png"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("deltachat", "deltachat-android"),
            title = "Delta Chat",
            publisher = "deltachat",
            summary = "Мессенджер поверх обычной почты с шифрованием: работает с любым почтовым ящиком, без номера телефона.",
            summaryEn = "Encrypted messenger on top of ordinary email: works with any mailbox, " +
                "no phone number needed.",
            categories = listOf(GitHubCategories.MESSENGERS),
            curated = true,
            assetNamePattern = "^deltachat-gplay-release",
            packageName = "chat.delta",
            iconOverrideUrl = "https://raw.githubusercontent.com/deltachat/deltachat-android/main/fastlane/metadata/android/en-US/images/icon.png"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("M66B", "FairEmail"),
            title = "FairEmail",
            publisher = "M66B",
            summary = "Почтовый клиент для нескольких ящиков с упором на приватность и шифрованием PGP и S/MIME.",
            summaryEn = "Email client for several mailboxes, focused on privacy, with PGP and " +
                "S/MIME encryption.",
            categories = listOf(GitHubCategories.PRIVACY, GitHubCategories.UTILITIES),
            curated = true,
            assetNamePattern = "-github-release",
            packageName = "eu.faircode.email",
            iconOverrideUrl = "https://raw.githubusercontent.com/M66B/FairEmail/master/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("yuliskov", "SmartTube"),
            title = "SmartTube",
            publisher = "yuliskov",
            summary = "YouTube без рекламы для Android TV и ТВ-приставок.",
            summaryEn = "Ad-free YouTube for Android TV and TV boxes.",
            categories = listOf(GitHubCategories.MEDIA),
            curated = true,
            assetNamePattern = "^SmartTube_stable"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("TeamNewPipe", "NewPipe"),
            title = "NewPipe",
            publisher = "TeamNewPipe",
            summary = "Лёгкий клиент YouTube без рекламы и аккаунта Google, с фоновым воспроизведением и загрузкой видео.",
            summaryEn = "Light YouTube client with no ads and no Google account, with background " +
                "play and video downloads.",
            categories = listOf(GitHubCategories.MEDIA),
            curated = true,
            assetNamePattern = "^NewPipe_",
            packageName = "org.schabi.newpipe",
            iconOverrideUrl = "https://raw.githubusercontent.com/TeamNewPipe/NewPipe/dev/fastlane/metadata/android/en-US/images/icon.png"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("InfinityLoop1308", "PipePipe"),
            title = "PipePipe",
            publisher = "InfinityLoop1308",
            summary = "Ответвление NewPipe с поддержкой YouTube и BiliBili и расширенными настройками плеера.",
            summaryEn = "NewPipe fork with YouTube and BiliBili support and extended player " +
                "settings.",
            categories = listOf(GitHubCategories.MEDIA),
            curated = true,
            assetNamePattern = "^PipePipe_",
            packageName = "InfinityLoop1309.NewPipeEnhanced",
            iconOverrideUrl = "https://raw.githubusercontent.com/InfinityLoop1308/PipePipe/main/fastlane/metadata/android/en-US/images/icon.png"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("libre-tube", "LibreTube"),
            title = "LibreTube",
            publisher = "libre-tube",
            summary = "YouTube без рекламы и без аккаунта Google через инстансы Piped.",
            summaryEn = "YouTube with no ads and no Google account, through Piped instances.",
            categories = listOf(GitHubCategories.MEDIA),
            curated = true,
            packageName = "com.github.libretube"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("ReVanced", "revanced-manager"),
            title = "ReVanced Manager",
            publisher = "ReVanced",
            summary = "Ставит патчи на YouTube и другие приложения: убирает рекламу и добавляет фоновое воспроизведение.",
            summaryEn = "Patches YouTube and other apps: removes ads and adds background play.",
            categories = listOf(GitHubCategories.MEDIA, GitHubCategories.UTILITIES),
            curated = true,
            assetNamePattern = "^revanced-manager",
            packageName = "app.revanced.manager.flutter"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("deniscerri", "ytdlnis"),
            title = "YTDLnis",
            publisher = "deniscerri",
            summary = "Графическая оболочка yt-dlp: скачивание видео и аудио для офлайна.",
            summaryEn = "yt-dlp front end: downloads video and audio for offline use.",
            categories = listOf(GitHubCategories.MEDIA),
            curated = true,
            packageName = "com.deniscerri.ytdl"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("anilibria", "anilibria-app"),
            title = "AniLiberty",
            publisher = "anilibria",
            summary = "Клиент AniLibria (теперь AniLiberty) — аниме с русской озвучкой. Lite-версия без рекламы.",
            summaryEn = "AniLibria (now AniLiberty) client — anime with Russian dubbing. The " +
                "Lite version has no ads.",
            categories = listOf(GitHubCategories.MEDIA),
            curated = true,
            // The lite build is the store flavor, with its own package id; the other APK is a different app id.
            assetNamePattern = "-lite",
            packageName = "tv.anilibria.store",
            iconOverrideUrl = "https://raw.githubusercontent.com/anilibria/anilibria-app/develop/app-mobile/src/main/res/mipmap-xxxhdpi/ic_launcher.png"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("aniyomiorg", "aniyomi"),
            title = "Aniyomi",
            publisher = "aniyomiorg",
            summary = "Аниме и манга из подключаемых источников в одном приложении, на основе Tachiyomi.",
            summaryEn = "Anime and manga from pluggable sources in one app, based on Tachiyomi.",
            categories = listOf(GitHubCategories.MEDIA, GitHubCategories.READING),
            curated = true,
            assetNamePattern = "^aniyomi-",
            packageName = "xyz.jmir.tachiyomi.mi",
            iconOverrideUrl = "https://raw.githubusercontent.com/aniyomiorg/aniyomi/main/fastlane/metadata/android/en-US/images/icon.png"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("mpv-android", "mpv-android"),
            title = "mpv",
            publisher = "mpv-android",
            summary = "Видеоплеер на mpv: играет почти любые форматы, субтитры и сетевые потоки.",
            summaryEn = "Video player on mpv: plays almost any format, subtitles and network " +
                "streams.",
            categories = listOf(GitHubCategories.MEDIA),
            curated = true,
            assetNamePattern = "^app-default-",
            packageName = "is.xyz.mpv",
            iconOverrideUrl = "https://raw.githubusercontent.com/mpv-android/mpv-android/master/fastlane/metadata/android/en-US/images/icon.png"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("moneytoo", "Player"),
            title = "Just Player",
            publisher = "moneytoo",
            summary = "Простой видеоплеер на ExoPlayer без рекламы: HDR, внешние субтитры, несколько звуковых дорожек.",
            summaryEn = "Simple ad-free video player on ExoPlayer: HDR, external subtitles, " +
                "several audio tracks.",
            categories = listOf(GitHubCategories.MEDIA),
            curated = true,
            packageName = "com.brouken.player",
            iconOverrideUrl = "https://raw.githubusercontent.com/moneytoo/Player/master/fastlane/metadata/android/en-US/images/icon.png"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("anilbeesetti", "nextplayer"),
            title = "NextPlayer",
            publisher = "anilbeesetti",
            summary = "Видеоплеер без рекламы с жестами управления и воспроизведением из папок на телефоне.",
            summaryEn = "Ad-free video player with gesture controls and playback from folders on " +
                "the phone.",
            categories = listOf(GitHubCategories.MEDIA),
            curated = true,
            assetNamePattern = "^nextplayer-",
            packageName = "dev.anilbeesetti.nextplayer",
            iconOverrideUrl = "https://raw.githubusercontent.com/anilbeesetti/nextplayer/main/fastlane/metadata/android/en-US/images/icon.png"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("mostafaalagamy", "Metrolist"),
            title = "Metrolist",
            publisher = "mostafaalagamy",
            summary = "Клиент YouTube Music без рекламы, с фоновым воспроизведением.",
            summaryEn = "Ad-free YouTube Music client with background play.",
            categories = listOf(GitHubCategories.MUSIC),
            curated = true
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("maxrave-dev", "SimpMusic"),
            title = "SimpMusic",
            publisher = "maxrave-dev",
            summary = "Клиент YouTube Music без рекламы: фоновое воспроизведение, тексты песен и офлайн-режим.",
            summaryEn = "Ad-free YouTube Music client: background play, lyrics and offline mode.",
            categories = listOf(GitHubCategories.MUSIC),
            curated = true,
            // The foss build is the one without Google services; the full one needs them.
            assetNamePattern = "-foss-",
            packageName = "com.maxrave.simpmusic",
            iconOverrideUrl = "https://raw.githubusercontent.com/maxrave-dev/SimpMusic/dev/fastlane/metadata/android/en-US/images/icon.png"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("OuterTune", "OuterTune"),
            title = "OuterTune",
            publisher = "OuterTune",
            summary = "Клиент YouTube Music без рекламы с библиотекой на телефоне и сохранением треков.",
            summaryEn = "Ad-free YouTube Music client with a library on the phone and saved " +
                "tracks.",
            categories = listOf(GitHubCategories.MUSIC),
            curated = true,
            assetNamePattern = "-full-",
            packageName = "com.dd3boh.outertune",
            iconOverrideUrl = "https://raw.githubusercontent.com/OuterTune/OuterTune/lite/fastlane/metadata/android/en-US/images/icon.png"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("team-spotube", "spotube"),
            title = "Spotube",
            publisher = "team-spotube",
            summary = "Плеер с каталогом и плейлистами Spotify без подписки Premium: звук берётся из других источников.",
            summaryEn = "Player with Spotify's catalogue and playlists and no Premium: the audio " +
                "comes from other sources.",
            categories = listOf(GitHubCategories.MUSIC),
            curated = true,
            assetNamePattern = "android",
            packageName = "oss.krtirtho.spotube",
            iconOverrideUrl = "https://raw.githubusercontent.com/team-spotube/spotube/master/android/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("namidaco", "namida"),
            title = "Namida",
            publisher = "namidaco",
            summary = "Плеер музыки и видео на телефоне с историей прослушиваний и режимом YouTube без рекламы.",
            summaryEn = "Music and video player for files on the phone, with listening history " +
                "and an ad-free YouTube mode.",
            categories = listOf(GitHubCategories.MUSIC),
            curated = true,
            assetNamePattern = "^namida-",
            packageName = "com.msob7y.namida"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("OxygenCobalt", "Auxio"),
            title = "Auxio",
            publisher = "OxygenCobalt",
            summary = "Простой и быстрый плеер музыки на телефоне, без рекламы и без доступа в интернет.",
            summaryEn = "Simple, fast music player for files on the phone, with no ads and no " +
                "internet access.",
            categories = listOf(GitHubCategories.MUSIC),
            curated = true,
            assetNamePattern = "^Auxio_",
            packageName = "org.oxycblt.auxio",
            iconOverrideUrl = "https://raw.githubusercontent.com/OxygenCobalt/Auxio/dev/fastlane/metadata/android/en-US/images/icon.png"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("FoedusProgramme", "Gramophone"),
            title = "Gramophone",
            publisher = "FoedusProgramme",
            summary = "Плеер музыки на телефоне в стиле Material You, с FLAC и ALAC.",
            summaryEn = "Material You music player for files on the phone, with FLAC and ALAC.",
            categories = listOf(GitHubCategories.MUSIC),
            curated = true,
            assetNamePattern = "-release",
            packageName = "org.akanework.gramophone",
            iconOverrideUrl = "https://raw.githubusercontent.com/FoedusProgramme/Gramophone/beta/fastlane/metadata/android/en-US/images/icon.png"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("mihonapp", "mihon"),
            title = "Mihon",
            publisher = "mihonapp",
            summary = "Читалка манги с подключаемыми источниками и библиотекой — продолжение Tachiyomi.",
            summaryEn = "Manga reader with pluggable sources and a library — the continuation of " +
                "Tachiyomi.",
            categories = listOf(GitHubCategories.READING),
            curated = true,
            assetNamePattern = "^mihon-",
            packageName = "app.mihon",
            iconOverrideUrl = "https://raw.githubusercontent.com/mihonapp/mihon/main/fastlane/metadata/android/en-US/images/icon.png"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("komikku-app", "komikku"),
            title = "Komikku",
            publisher = "komikku-app",
            summary = "Форк Mihon с дополнительными настройками чтения, фильтрами и сортировкой.",
            summaryEn = "Mihon fork with extra reading settings, filters and sorting.",
            categories = listOf(GitHubCategories.READING),
            curated = true,
            assetNamePattern = "^Komikku-",
            packageName = "app.komikku"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("koreader", "koreader"),
            title = "KOReader",
            publisher = "koreader",
            summary = "Читалка книг для PDF, DjVu и EPUB с тонкой настройкой отображения.",
            summaryEn = "Book reader for PDF, DjVu and EPUB with fine-grained display settings.",
            categories = listOf(GitHubCategories.READING),
            curated = true,
            // Releases also carry builds for e-readers and desktops.
            assetNamePattern = "^koreader-android-",
            packageName = "org.koreader.launcher"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("proninyaroslav", "libretorrent"),
            title = "LibreTorrent",
            publisher = "proninyaroslav",
            summary = "Торрент-клиент без рекламы: magnet-ссылки, планировщик и выбор файлов раздачи.",
            summaryEn = "Ad-free torrent client: magnet links, a scheduler and picking files in " +
                "a torrent.",
            categories = listOf(GitHubCategories.TORRENTS),
            curated = true,
            assetNamePattern = "^LibreTorrent-",
            packageName = "org.proninyaroslav.libretorrent",
            iconOverrideUrl = "https://raw.githubusercontent.com/proninyaroslav/libretorrent/master/app/src/release/res/mipmap-xxxhdpi/ic_launcher.png"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("localsend", "localsend"),
            title = "LocalSend",
            publisher = "localsend",
            summary = "Передача файлов между устройствами по локальной сети — без облака и без регистрации.",
            summaryEn = "Sends files between devices over the local network — no cloud, no " +
                "sign-up.",
            categories = listOf(GitHubCategories.FILES),
            curated = true,
            assetNamePattern = "android",
            packageName = "org.localsend.localsend_app"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("zhanghai", "MaterialFiles"),
            title = "Material Files",
            publisher = "zhanghai",
            summary = "Файловый менеджер в стиле Material без рекламы, с доступом по SFTP, SMB и FTP.",
            summaryEn = "Ad-free Material file manager with SFTP, SMB and FTP access.",
            categories = listOf(GitHubCategories.FILES),
            curated = true,
            packageName = "me.zhanghai.android.files",
            iconOverrideUrl = "https://raw.githubusercontent.com/zhanghai/MaterialFiles/master/fastlane/metadata/android/en-US/images/icon.png"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("TeamAmaze", "AmazeFileManager"),
            title = "Amaze File Manager",
            publisher = "TeamAmaze",
            summary = "Файловый менеджер с архивами, облачными хранилищами и поддержкой root.",
            summaryEn = "File manager with archives, cloud storage and root support.",
            categories = listOf(GitHubCategories.FILES),
            curated = true,
            assetNamePattern = "-fdroid-",
            packageName = "com.amaze.filemanager",
            iconOverrideUrl = "https://raw.githubusercontent.com/TeamAmaze/AmazeFileManager/release/4.0/fastlane/metadata/android/en-US/images/icon.png"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("Catfriend1", "syncthing-android"),
            title = "Syncthing-Fork",
            publisher = "Catfriend1",
            summary = "P2P-синхронизация файлов без облака. Поддерживаемое продолжение Syncthing для Android — официальный клиент архивирован.",
            summaryEn = "Peer-to-peer file sync with no cloud. The maintained continuation of " +
                "Syncthing for Android — the official client is archived.",
            categories = listOf(GitHubCategories.FILES, GitHubCategories.BACKUP),
            curated = true
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("nextcloud", "android"),
            title = "Nextcloud",
            publisher = "nextcloud",
            summary = "Клиент своего облака Nextcloud: синхронизация файлов и фото со своим сервером.",
            summaryEn = "Client for your own Nextcloud: syncs files and photos with your server.",
            categories = listOf(GitHubCategories.FILES, GitHubCategories.BACKUP),
            curated = true,
            assetNamePattern = "^nextcloud-",
            packageName = "com.nextcloud.client",
            iconOverrideUrl = "https://raw.githubusercontent.com/nextcloud/android/master/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("immich-app", "immich"),
            title = "Immich",
            publisher = "immich-app",
            summary = "Клиент своего фотосервера Immich: копирует фото и видео с телефона на ваш сервер вместо чужого облака.",
            summaryEn = "Client for your own Immich photo server: copies photos and videos from " +
                "the phone to your server instead of someone else's cloud.",
            categories = listOf(GitHubCategories.PHOTOS, GitHubCategories.BACKUP),
            curated = true,
            packageName = "app.alextran.immich",
            iconOverrideUrl = "https://raw.githubusercontent.com/immich-app/immich/main/mobile/android/fastlane/metadata/android/en-US/images/icon.png"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("ente-io", "ente"),
            title = "Ente Photos",
            publisher = "ente-io",
            summary = "Резервное копирование фото со сквозным шифрованием — замена Google Фото.",
            summaryEn = "End-to-end encrypted photo backup — a replacement for Google Photos.",
            categories = listOf(GitHubCategories.PHOTOS, GitHubCategories.BACKUP),
            curated = true,
            assetNamePattern = "^ente-photos"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("FossifyOrg", "Gallery"),
            title = "Fossify Gallery",
            publisher = "FossifyOrg",
            summary = "Галерея без рекламы и лишних разрешений, с редактором и скрытыми папками.",
            summaryEn = "Gallery with no ads and no needless permissions, with an editor and " +
                "hidden folders.",
            categories = listOf(GitHubCategories.PHOTOS),
            curated = true,
            assetNamePattern = "-foss-",
            packageName = "org.fossify.gallery",
            iconOverrideUrl = "https://raw.githubusercontent.com/FossifyOrg/Gallery/main/fastlane/metadata/android/en-US/images/icon.png"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("T8RIN", "ImageToolbox"),
            title = "Image Toolbox",
            publisher = "T8RIN",
            summary = "Редактор и конвертер картинок: размер и форматы, водяные знаки, распознавание текста, удаление фона.",
            summaryEn = "Image editor and converter: size and formats, watermarks, text " +
                "recognition, background removal.",
            categories = listOf(GitHubCategories.PHOTOS, GitHubCategories.UTILITIES),
            curated = true,
            assetNamePattern = "^image-toolbox-",
            packageName = "ru.tech.imageresizershrinker",
            iconOverrideUrl = "https://raw.githubusercontent.com/T8RIN/ImageToolbox/master/fastlane/metadata/android/en-US/images/icon.png"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("NeoApplications", "Neo-Backup"),
            title = "Neo Backup",
            publisher = "NeoApplications",
            summary = "Резервные копии приложений вместе с данными. Нужен root.",
            summaryEn = "Backs up apps together with their data. Needs root.",
            categories = listOf(GitHubCategories.BACKUP),
            curated = true,
            assetNamePattern = "^Neo_Backup_",
            packageName = "com.machiav3lli.backup",
            iconOverrideUrl = "https://raw.githubusercontent.com/NeoApplications/Neo-Backup/main/fastlane/metadata/android/en-US/images/icon.png"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("Kunzisoft", "KeePassDX"),
            title = "KeePassDX",
            publisher = "Kunzisoft",
            summary = "Менеджер паролей для баз KeePass: всё хранится в зашифрованном файле на телефоне, без облака.",
            summaryEn = "Password manager for KeePass databases: everything is kept in an " +
                "encrypted file on the phone, no cloud.",
            categories = listOf(GitHubCategories.SECURITY),
            curated = true,
            // The libre build is the one published as an app of its own; the pro flavor has another id.
            assetNamePattern = "-libre",
            packageName = "com.kunzisoft.keepass.libre",
            iconOverrideUrl = "https://raw.githubusercontent.com/Kunzisoft/KeePassDX/master/app/src/libre/res/mipmap-xxxhdpi/ic_launcher.png"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("bitwarden", "android"),
            title = "Bitwarden",
            publisher = "bitwarden",
            summary = "Менеджер паролей с синхронизацией через облако или свой сервер.",
            summaryEn = "Password manager that syncs through the cloud or your own server.",
            categories = listOf(GitHubCategories.SECURITY),
            curated = true,
            // The same repository releases the Authenticator app under its own tags.
            assetNamePattern = "^com\\.x8bit\\.bitwarden\\.apk$",
            packageName = "com.x8bit.bitwarden"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("beemdevelopment", "Aegis"),
            title = "Aegis Authenticator",
            publisher = "beemdevelopment",
            summary = "Коды двухфакторной аутентификации с зашифрованными резервными копиями.",
            summaryEn = "Two-factor authentication codes with encrypted backups.",
            categories = listOf(GitHubCategories.SECURITY),
            curated = true,
            assetNamePattern = "^aegis-",
            packageName = "com.beemdevelopment.aegis",
            iconOverrideUrl = "https://raw.githubusercontent.com/beemdevelopment/Aegis/master/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("stratumauth", "app"),
            title = "Stratum",
            publisher = "stratumauth",
            summary = "Офлайновый генератор кодов двухфакторной аутентификации.",
            summaryEn = "Offline two-factor authentication code generator.",
            categories = listOf(GitHubCategories.SECURITY),
            curated = true
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("uazo", "cromite"),
            title = "Cromite",
            publisher = "uazo",
            summary = "Браузер на Chromium со встроенной блокировкой рекламы и трекеров.",
            summaryEn = "Chromium-based browser with built-in ad and tracker blocking.",
            categories = listOf(GitHubCategories.BROWSERS),
            curated = true,
            assetNamePattern = "ChromePublic"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("duckduckgo", "Android"),
            title = "DuckDuckGo",
            publisher = "duckduckgo",
            summary = "Браузер с блокировкой трекеров и принудительным HTTPS, поиск без слежки.",
            summaryEn = "Browser with tracker blocking and enforced HTTPS, and search without " +
                "tracking.",
            categories = listOf(GitHubCategories.BROWSERS, GitHubCategories.PRIVACY),
            curated = true,
            assetNamePattern = "-play-release",
            packageName = "com.duckduckgo.mobile.android",
            iconOverrideUrl = "https://raw.githubusercontent.com/duckduckgo/Android/develop/fastlane/metadata/android/en-US/images/icon.png"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("HeliBorg", "HeliBoard"),
            title = "HeliBoard",
            publisher = "HeliBorg",
            summary = "Клавиатура без рекламы и сбора данных на основе AOSP, со своими словарями и раскладками, включая русскую.",
            summaryEn = "AOSP-based keyboard with no ads and no data collection, with its own " +
                "dictionaries and layouts, Russian included.",
            categories = listOf(GitHubCategories.KEYBOARDS),
            curated = true,
            assetNamePattern = "-release",
            packageName = "helium314.keyboard",
            iconOverrideUrl = "https://raw.githubusercontent.com/HeliBorg/HeliBoard/main/fastlane/metadata/android/en-US/images/icon.png"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("florisboard", "florisboard"),
            title = "FlorisBoard",
            publisher = "florisboard",
            summary = "Открытая клавиатура с темами, жестовым вводом и настраиваемыми раскладками.",
            summaryEn = "Open-source keyboard with themes, gesture typing and customisable " +
                "layouts.",
            categories = listOf(GitHubCategories.KEYBOARDS),
            curated = true,
            assetNamePattern = "-stable",
            packageName = "dev.patrickgold.florisboard",
            iconOverrideUrl = "https://raw.githubusercontent.com/florisboard/florisboard/main/fastlane/metadata/android/en-US/images/icon.png"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("MuntashirAkon", "AppManager"),
            title = "App Manager",
            publisher = "MuntashirAkon",
            summary = "Менеджер приложений: компоненты и разрешения, блокировка трекеров, бэкапы и установка APK.",
            summaryEn = "App manager: components and permissions, tracker blocking, backups and " +
                "APK installs.",
            categories = listOf(GitHubCategories.UTILITIES),
            curated = true,
            assetNamePattern = "^AppManager_",
            packageName = "io.github.muntashirakon.AppManager",
            iconOverrideUrl = "https://raw.githubusercontent.com/MuntashirAkon/AppManager/master/fastlane/metadata/android/en-US/images/icon.png"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("samolego", "Canta"),
            title = "Canta",
            publisher = "samolego",
            summary = "Удаляет и отключает предустановленные приложения без root, через Shizuku.",
            summaryEn = "Removes and disables preinstalled apps without root, through Shizuku.",
            categories = listOf(GitHubCategories.UTILITIES),
            curated = true,
            packageName = "io.github.samolego.canta",
            iconOverrideUrl = "https://raw.githubusercontent.com/samolego/Canta/master/androidApp/src/main/res/mipmap-xxxhdpi/ic_launcher.png"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("aistra0528", "Hail"),
            title = "Hail",
            publisher = "aistra0528",
            summary = "Замораживает приложения через Shizuku или root, чтобы они не работали в фоне и не тратили заряд.",
            summaryEn = "Freezes apps through Shizuku or root so they do not run in the " +
                "background and drain the battery.",
            categories = listOf(GitHubCategories.UTILITIES),
            curated = true,
            assetNamePattern = "^Hail-",
            packageName = "com.aistra.hail",
            iconOverrideUrl = "https://raw.githubusercontent.com/aistra0528/Hail/main/fastlane/metadata/android/en-US/images/icon.png"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("wxxsfxyzm", "InstallerX-Revived"),
            title = "InstallerX Revived",
            publisher = "wxxsfxyzm",
            summary = "Замена системного установщика APK: Shizuku, пакеты из нескольких APK и настройки установки.",
            summaryEn = "Replacement for the system APK installer: Shizuku, split APK packages " +
                "and install settings.",
            categories = listOf(GitHubCategories.UTILITIES),
            curated = true,
            assetNamePattern = "-online-"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("organicmaps", "organicmaps"),
            title = "Organic Maps",
            publisher = "organicmaps",
            summary = "Офлайн-карты на OpenStreetMap с навигацией, без рекламы и слежки.",
            summaryEn = "Offline OpenStreetMap maps with navigation, no ads and no tracking.",
            categories = listOf(GitHubCategories.UTILITIES),
            curated = true,
            // Android releases share the repository with other platforms and carry the web flavor.
            assetNamePattern = "-web-release",
            packageName = "app.organicmaps.web"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("gsantner", "markor"),
            title = "Markor",
            publisher = "gsantner",
            summary = "Заметки в Markdown и обычном тексте, которые лежат простыми файлами.",
            summaryEn = "Notes in Markdown and plain text, kept as ordinary files.",
            categories = listOf(GitHubCategories.UTILITIES),
            curated = true,
            assetNamePattern = "flavorDefault",
            packageName = "net.gsantner.markor"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("breezy-weather", "breezy-weather"),
            title = "Breezy Weather",
            publisher = "breezy-weather",
            summary = "Погода без рекламы с несколькими источниками прогноза и виджетами.",
            summaryEn = "Ad-free weather with several forecast sources and widgets.",
            categories = listOf(GitHubCategories.WEATHER),
            curated = true,
            assetNamePattern = "_standard",
            packageName = "org.breezyweather",
            iconOverrideUrl = "https://raw.githubusercontent.com/breezy-weather/breezy-weather/main/fastlane/metadata/android/en-US/images/icon.png"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("ankidroid", "Anki-Android"),
            title = "AnkiDroid",
            publisher = "ankidroid",
            summary = "Карточки с интервальным повторением для языков и запоминания, синхронизация с Anki.",
            summaryEn = "Spaced-repetition flashcards for languages and memorising, synced with " +
                "Anki.",
            categories = listOf(GitHubCategories.LEARNING),
            curated = true,
            assetNamePattern = "-full-",
            packageName = "com.ichi2.anki",
            iconOverrideUrl = "https://raw.githubusercontent.com/ankidroid/Anki-Android/main/fastlane/metadata/android/en-US/images/icon.png"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("ImranR98", "Obtainium"),
            title = "Obtainium",
            publisher = "ImranR98",
            summary = "Устанавливает и обновляет приложения напрямую из релизов GitHub, GitLab и F-Droid.",
            summaryEn = "Installs and updates apps straight from GitHub, GitLab and F-Droid " +
                "releases.",
            categories = listOf(GitHubCategories.STORES),
            curated = true,
            packageName = "dev.imranr.obtainium"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("Droid-ify", "client"),
            title = "Droid-ify",
            publisher = "Droid-ify",
            summary = "Быстрый клиент F-Droid — доступ ко всему каталогу свободных приложений.",
            summaryEn = "Fast F-Droid client — the whole catalogue of free apps.",
            categories = listOf(GitHubCategories.STORES),
            curated = true,
            packageName = "com.looker.droidify"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("NeoApplications", "Neo-Store"),
            title = "Neo Store",
            publisher = "NeoApplications",
            summary = "Альтернативный клиент F-Droid с современным интерфейсом.",
            summaryEn = "Alternative F-Droid client with a modern interface.",
            categories = listOf(GitHubCategories.STORES),
            curated = true,
            packageName = "com.machiav3lli.fdroid"
        ),
        GitHubCatalogEntry(
            repository = GitHubRepository("wyrtensi", "CapturePort"),
            title = "CapturePort",
            publisher = "wyrtensi",
            summary = "CapturePort — захват и передача экрана.",
            summaryEn = "CapturePort — screen capture and casting.",
            categories = listOf(GitHubCategories.UTILITIES),
            curated = true,
            packageName = "dev.captureport.app",
            iconOverrideUrl = "https://raw.githubusercontent.com/wyrtensi/CapturePort/HEAD/android/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png"
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
                    entry.summaryEn.orEmpty().lowercase().contains(needle) ||
                    entry.categories.any { GitHubCategories.matches(it, needle) }
            }
            .sortedWith(
                compareByDescending<GitHubCatalogEntry> { it.slug.equals(needle, ignoreCase = true) }
                    .thenByDescending { it.title.equals(needle, ignoreCase = true) }
                    .thenByDescending { it.curated }
                    .thenBy { it.title.lowercase() }
            )
    }

    /**
     * The short GitHub rail on the home screen.
     *
     * The catalogue is ordered by relevance within its sections and the network tools come first,
     * so its first dozen were all VPN and proxy clients and the rail read as a VPN shelf. It takes
     * one project from each section in turn instead, keeping the catalogue's order within a section.
     * Repositories the user added come first - they are the ones this person actually follows.
     */
    fun homeRail(entries: List<GitHubCatalogEntry>, limit: Int): List<GitHubCatalogEntry> {
        val added = entries.filterNot { it.curated }
        val bySection = entries.filter { it.curated }
            .groupBy { it.categories.firstOrNull().orEmpty() }
            .values
            .map { ArrayDeque(it) }
        val mixed = mutableListOf<GitHubCatalogEntry>()
        while (bySection.any { it.isNotEmpty() }) {
            bySection.forEach { section -> section.removeFirstOrNull()?.let(mixed::add) }
        }
        return (added + mixed).take(limit)
    }

    fun find(slug: String, userRepositories: List<GitHubRepository> = emptyList()): GitHubCatalogEntry? =
        entries(userRepositories).firstOrNull { it.slug.equals(slug, ignoreCase = true) }

    /** The entry known to publish [packageName], so adopting that app can offer its repository. */
    fun findByPackage(packageName: String): GitHubCatalogEntry? =
        entries().firstOrNull { it.packageName == packageName }

    /**
     * The entry a queue row belongs to, found by the placeholder name the row was created with.
     *
     * A release asset has no package name until the APK is opened, so a GitHub row carries a
     * placeholder instead - and a placeholder matches nothing in the catalogue cache, which is why
     * these rows were the only ones in the list with no icon.
     */
    fun findByPlaceholder(
        placeholder: String,
        userRepositories: List<GitHubRepository> = emptyList()
    ): GitHubCatalogEntry? = entries(userRepositories).firstOrNull {
        dev.wystore.updates.GitHubInstallScheduler.placeholderPackageName(it.repository) == placeholder
    }
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
