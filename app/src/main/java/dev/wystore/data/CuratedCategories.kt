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

object CuratedCategories {

    private const val ICON = "https://static.rustore.ru/icon"

    val ALL: List<CuratedCategory> = listOf(
        CuratedCategory(
            slug = "wy-banks",
            title = "Банки",
            iconUrl = "$ICON/finance.svg",
            sourceSlugs = listOf("finance"),
            packages = listOf(
                "ru.sberbankmobile",
                "com.idamob.tinkoff.android",
                "ru.vtb24.mobilebanking.android",
                "ru.alfabank.mobile.android",
                "ru.gazprombank.android.mobilebank.app",
                "logo.com.mbanking",
                "ru.ozon.fintech.finance",
                "ru.wildberries.bank",
                "com.yandex.bank",
                "ru.lewis.dbo",
                "ru.sovcomcard.halva.v1",
                "ru.otpbank.mobile",
                "ru.bankuralsib.mb.android",
                "ru.yoo.money",
                "ru.nspk.mirpay",
                "ru.sberbank.investor",
                "com.moex.finuslugi",
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
                "ru.aliexpress.buyer",
                "ru.ozon.select",
                "com.lamoda.lite",
                "ru.dns.shop.android",
                "ru.filit.mvideo.b2c",
                "ru.sportmaster.app",
                "ru.detmir.dmbonus",
                "ru.leroymerlin.mobile",
                "goldapple.ru.goldapple.customers",
                "ru.letu",
                "ru.sunlight.sunlight",
                "ru.zolotoy585.customer",
                "ru.kari.android",
                "ru.bestprice.fixprice",
                "com.logistic.sdek",
                "ru.cardsmobile.mw3"
            )
        ),
        CuratedCategory(
            slug = "wy-food",
            title = "Супермаркеты и рестораны",
            iconUrl = "$ICON/foodAndDrink.svg",
            sourceSlugs = listOf("purchases"),
            packages = listOf(
                "ru.tander.magnit",
                "ru.pyaterochka.app.browser",
                "ru.perekrestok.app",
                "ru.lenta.lentochka",
                "ru.vkusvill",
                "ru.vcr.verniy_all",
                "ru.sbcs.store",
                "ru.instamart",
                "ru.foodfox.client",
                "com.apegroup.mcdonaldsrussia",
                "ru.burgerking",
                "ru.kfc.kfc_delivery",
                "ru.dodopizza.app",
                "ru.papajohns.app",
                "com.edadeal.android"
            )
        ),
        CuratedCategory(
            slug = "wy-state",
            title = "Государственное",
            iconUrl = "$ICON/state.svg",
            sourceSlugs = listOf("state", "finance"),
            packages = listOf(
                "ru.rostel",
                "ru.sigma.gisgkh",
                "ru.gosuslugi.auto",
                "ru.gosuslugi.goskey",
                "ru.rtlabs.mobile.ebs.gosuslugi.android",
                "ru.gosuslugi.culture",
                "ru.gosuslugi.pos",
                "ru.fanid",
                "ru.altarix.mos.pgu",
                "ru.gosuslugi.migrant",
                "ru.fns.lkfl",
                "com.gnivts.selfemployed",
                "ru.gnivc.lkip",
                "ru.sitesoft.fssp",
                "ru.netvoxlab.mydocapp",
                "ru.mos.app",
                "ru.mos.ed",
                "ru.trudvsem.mobile",
                "io.citizens.security",
                "ru.mosreg.uslugi.mobile.beta"
            )
        ),
        CuratedCategory(
            slug = "wy-social",
            title = "Мессенджеры и соцсети",
            iconUrl = "$ICON/social.svg",
            sourceSlugs = listOf("social"),
            packages = listOf(
                "ru.oneme.app",
                "org.telegram.messenger.web",
                "com.vkontakte.android",
                "com.vk.im",
                "ru.ok.android",
                "ru.zen.android",
                "com.vk.clips",
                "live.vkplay.app",
                "ru.yandex.telemost",
                "us.zoom.videomeetings",
                "com.vk.love",
                "ru.ok.dating",
                "ru.mamba.client",
                "com.pinterest"
            )
        ),
        CuratedCategory(
            slug = "wy-life",
            title = "Для жизни",
            iconUrl = "$ICON/lifestyle.svg",
            sourceSlugs = listOf("tools", "transport"),
            packages = listOf(
                "ru.yandex.mail",
                "ru.mail.mailapp",
                "ru.yandex.yandexmaps",
                "ru.yandex.yandexnavi",
                "ru.dublgis.dgismobile",
                "ru.yandex.taxi",
                "ru.yandex.weatherplugin",
                "ru.yandex.disk",
                "ru.mail.cloud",
                "com.yandex.browser",
                "ru.yandex.metro",
                "ru.mosmetro.metro",
                "by.advasoft.android.troika.app",
                "ru.gibdd_pay.app",
                "ru.mosparking.appnew",
                "ru.urentbike.app",
                "com.carshering",
                "com.kms.free"
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
