package dev.wystore.data

/**
 * A Wy Store category, assembled from the source catalogue rather than mirroring it.
 *
 * RuStore's own sections are broad ("Финансы", "Покупки") and mix, for example, banks with payday
 * loan apps. These categories are the ones a user actually shops by, so each names the source
 * sections to pull cards from and, where the section is too broad, the exact packages that belong.
 *
 * @param sourceSlugs RuStore sections to read cards from, in order.
 * @param packages ordered allowlist. Empty means "everything the sections return".
 */
data class CuratedCategory(
    val slug: String,
    val title: String,
    val iconUrl: String?,
    val sourceSlugs: List<String>,
    val packages: List<String> = emptyList()
) {
    fun toStoreCategory(): StoreCategory = StoreCategory(slug = slug, title = title, iconUrl = iconUrl)

    /**
     * Orders and filters cards fetched from [sourceSlugs]. With an allowlist the result follows the
     * curated order; without one the source's own ranking is kept.
     */
    fun select(apps: List<StoreApp>): List<StoreApp> {
        val unique = apps.distinctBy { it.packageName }
        if (packages.isEmpty()) return unique
        val byPackage = unique.associateBy { it.packageName }
        return packages.mapNotNull { byPackage[it] }
    }

    /** Pages to read per source section; an allowlisted app can sit past the first page. */
    val pagesPerSource: Int get() = if (packages.isEmpty()) 1 else 2
}

/**
 * The curated sections offered on Home.
 *
 * Every package here was checked against the source catalogue rather than guessed: a name that does
 * not exist would simply be dropped by [CuratedCategory.select], leaving a category quietly short
 * of what it claims to cover.
 */
object CuratedCategories {

    private const val ICON = "https://static.rustore.ru/icon"

    val ALL: List<CuratedCategory> = listOf(
        CuratedCategory(
            slug = "wy-banks",
            title = "Банки и платежи",
            iconUrl = "$ICON/finance.svg",
            sourceSlugs = listOf("finance"),
            packages = listOf(
                "ru.sberbankmobile",
                "com.idamob.tinkoff.android",
                "ru.vtb24.mobilebanking.android",
                "ru.alfabank.mobile.android",
                "ru.gazprombank.android.mobilebank.app",
                "logo.com.mbanking",
                "ru.rshb.dbo",
                "ru.otpbank.mobile",
                "ru.bankuralsib.mb.android",
                "ru.sovcomcard.halva.v1",
                "ru.ozon.fintech.finance",
                "ru.wildberries.bank",
                "com.yandex.bank",
                "ru.lewis.dbo",
                "ru.yoo.money",
                "ru.nspk.mirpay",
                "ru.nspk.sbpay",
                "ru.sberbank.investor",
                "ru.tinkoff.investing",
                "com.moex.finuslugi",
                "ru.alfastrah.app",
                "ru.gibdd_pay.app",
                "ru.sberbank.sberkids"
            )
        ),
        CuratedCategory(
            slug = "wy-marketplaces",
            title = "Маркетплейсы",
            iconUrl = "$ICON/purchases.svg",
            sourceSlugs = listOf("purchases"),
            packages = listOf(
                "ru.ozon.app.android",
                "com.wildberries.ru",
                "ru.beru.android",
                "ru.megamarket.marketplace",
                "ru.aliexpress.buyer",
                "com.kazanexpress.ke_app",
                "ru.ozon.select",
                "com.lamoda.lite",
                "ru.dns.shop.android",
                "ru.filit.mvideo.b2c",
                "ru.sportmaster.app",
                "ru.detmir.dmbonus",
                "ru.bestprice.fixprice",
                "goldapple.ru.goldapple.customers",
                "ru.letu",
                "ru.rivegauche.app",
                "ru.sunlight.sunlight",
                "ru.sokolov.android",
                "ru.leroymerlin.mobile",
                "com.notissimus.allinstruments.android",
                "ru.kari.android",
                "com.gloriajeans.mobile",
                "com.logistic.sdek",
                "com.edadeal.android",
                "ru.cardsmobile.mw3",
                "com.flowwow"
            )
        ),
        CuratedCategory(
            slug = "wy-food",
            title = "Продукты и еда",
            iconUrl = "$ICON/foodAndDrink.svg",
            sourceSlugs = listOf("foodanddrink"),
            packages = listOf(
                "ru.tander.magnit",
                "ru.pyaterochka.app.browser",
                "com.icemobile.lenta.prod",
                "ru.perekrestok.app",
                "ru.vkusvill",
                "ru.sbcs.store",
                "com.yandex.lavka",
                "ru.instamart",
                "ru.ozon.fresh",
                "ru.myauchan.droid",
                "club.chizhik",
                "com.ru.dixy",
                "ru.vcr.verniy_all",
                "ru.mntk.dostavka.prod",
                "ru.reksoft.okey",
                "ru.myspar",
                "www.metro.com",
                "ru.globus.app",
                "ru.napoleonit.kb",
                "ru.bristol.bristol_app",
                "ru.winelab",
                "ru.foodfox.client",
                "com.deliveryclub",
                "ru.dodopizza.app",
                "com.apegroup.mcdonaldsrussia",
                "ru.kfc.kfc_delivery",
                "ru.burgerking",
                "ru.farfor",
                "ru.chibbis"
            )
        ),
        CuratedCategory(
            slug = "wy-state",
            title = "Госуслуги",
            iconUrl = "$ICON/state.svg",
            // "Налоги ФЛ" and "Мой налог" are filed under Финансы by the source but belong here.
            sourceSlugs = listOf("state", "finance"),
            packages = listOf(
                "ru.rostel",
                "ru.gosuslugi.goskey",
                "ru.fns.lkfl",
                "com.gnivts.selfemployed",
                "ru.sigma.gisgkh",
                "ru.gosuslugi.auto",
                "ru.rtlabs.mobile.ebs.gosuslugi.android",
                "ru.gosuslugi.pos",
                "ru.gosuslugi.culture",
                "ru.fanid",
                "ru.altarix.mos.pgu",
                "ru.mos.app",
                "ru.mos.ed",
                "ru.netvoxlab.mydocapp",
                "ru.sitesoft.fssp",
                "io.citizens.security",
                "ru.trudvsem.mobile",
                "ru.gnivc.lkip",
                "ru.mosreg.uslugi.mobile.beta",
                "ru.dobro"
            )
        ),
        CuratedCategory(
            slug = "wy-social",
            title = "Общение",
            iconUrl = "$ICON/social.svg",
            sourceSlugs = listOf("social", "tools"),
            packages = listOf(
                "ru.oneme.app",
                "org.telegram.messenger.web",
                "com.vkontakte.android",
                "ru.ok.android",
                "com.vk.im",
                "ru.zen.android",
                "ru.yandex.telemost",
                "live.vkplay.app",
                "ru.mail.mailapp",
                "ru.yandex.mail",
                "ru.mail.cloud"
            )
        ),
        CuratedCategory(
            slug = "wy-media",
            title = "Кино и музыка",
            iconUrl = "$ICON/entertainment.svg",
            sourceSlugs = listOf("entertainment"),
            packages = listOf(
                "com.vk.vkvideo",
                "ru.rutube.app",
                "ru.kinopoisk",
                "ru.ivi.client",
                "ru.more.play",
                "ru.rt.video.app.mobile",
                "ru.mts.mtstv",
                "ru.yandex.music",
                "com.uma.musicvk",
                "com.zvooq.openplay",
                "ru.mts.music.android",
                "ru.stoloto.mobile"
            )
        ),
        CuratedCategory(
            slug = "wy-transport",
            title = "Транспорт",
            iconUrl = "$ICON/transport.svg",
            sourceSlugs = listOf("transport"),
            packages = listOf(
                "ru.yandex.taxi",
                "ru.yandex.yandexmaps",
                "ru.yandex.yandexnavi",
                "ru.dublgis.dgismobile",
                "com.taxsee.taxsee",
                "youdrive.today",
                "ru.urentbike.app",
                "com.punicapp.whoosh",
                "ru.mosmetro.metro",
                "ru.russianhighways.mobile",
                "com.gpn.azs",
                "ru.serebryakovas.lukoilmobileapp",
                "ru.tatneft.gasstations",
                "ru.yandex.mobile.gasstations",
                "ru.drom.pdd.android.app"
            )
        ),
        CuratedCategory(
            slug = "wy-travel",
            title = "Путешествия",
            iconUrl = "$ICON/travelling.svg",
            sourceSlugs = listOf("travelling"),
            packages = listOf(
                "ru.rzd.pass",
                "ru.tutu.tutu_emp",
                "ru.yandex.travel",
                "ru.yandex.rasp",
                "ru.aviasales",
                "travel.ozon.mobile",
                "ru.aeroflot",
                "ru.s7.android",
                "ru.ostrovok.android",
                "ru.sutochno_redesign",
                "otello.dgis.ru",
                "com.avito.android",
                "com.mapswithme.maps.pro"
            )
        ),
        CuratedCategory(
            slug = "wy-health",
            title = "Здоровье и аптеки",
            iconUrl = "$ICON/health.svg",
            sourceSlugs = listOf("health"),
            packages = listOf(
                "com.programmisty.emiasapp",
                "com.docdoc.docdoc",
                "ru.medtochka",
                "ru.swan.kvrachu",
                "com.invitro.app",
                "ru.apteka",
                "com.apteka.sklad",
                "ru.uteka.app",
                "ru.getpharma.eapteka",
                "ru.zdravcity.app",
                "com.platfomni.vita",
                "com.aptekarsk.pz",
                "ru.neopharm.stolichki",
                "ru.apteki.plus",
                "com.platfomni.saas.ma",
                "ru.sogaz.app"
            )
        ),
        CuratedCategory(
            slug = "wy-telecom",
            title = "Связь",
            iconUrl = "$ICON/tools.svg",
            sourceSlugs = listOf("tools"),
            packages = listOf(
                "ru.mts.mymts",
                "ru.beeline.services",
                "ru.megafon.mlk",
                "ru.tele2.mytele2",
                "ru.yota.android",
                "ru.sber.telecom"
            )
        )
    )

    fun find(slug: String): CuratedCategory? = ALL.firstOrNull { it.slug == slug }

    /** Curated categories are shown first; the source's own sections stay available below them. */
    fun merge(sourceCategories: List<StoreCategory>): List<StoreCategory> {
        val curatedSlugs = ALL.mapTo(mutableSetOf()) { it.slug }
        return ALL.map { it.toStoreCategory() } + sourceCategories.filterNot { it.slug in curatedSlugs }
    }
}
