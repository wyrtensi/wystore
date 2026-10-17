package dev.wystore.data

/**
 * RuStore's section names as its web catalogue prints them, back to the slugs its API uses.
 *
 * A catalogue or search card carries the sections as Russian words joined by a middle dot -
 * "Финансы·Бизнес-сервисы" - while the API and the section links use "finance", "business". The
 * cards passed the words through, so an English home screen read "★ 4.7 · Развлечения". As slugs
 * they are named in the interface language like everywhere else.
 *
 * Games carry genres there instead ("Ролевые·Стратегии"), which have no slug of their own; those are
 * kept as written rather than dropped, because a genre is still information.
 */
object RuStoreCategoryNames {

    private const val SEPARATOR = '·'

    private val slugByName = mapOf(
        "объявления и услуги" to "adsandservices",
        "книги" to "books",
        "бизнес-сервисы" to "business",
        "образование" to "education",
        "развлечения" to "entertainment",
        "финансы" to "finance",
        "еда и напитки" to "foodanddrink",
        "ставки и лотереи" to "gambling",
        "игры" to "games",
        "здоровье" to "health",
        "образ жизни" to "lifestyle",
        "новости и события" to "news",
        "родителям" to "parenting",
        "питомцы" to "pets",
        "покупки" to "purchases",
        "общение" to "social",
        "спорт" to "sport",
        "государственные" to "state",
        "полезные инструменты" to "tools",
        "транспорт и навигация" to "transport",
        "путешествия" to "travelling"
    )

    /** The sections of one card line, each as its slug when RuStore has one for it. */
    fun slugsOf(label: String): List<String> =
        label.split(SEPARATOR)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { slugOf(it) ?: it }
            .distinct()

    /** The slug for a single section name, or null for a genre or a name RuStore added since. */
    fun slugOf(name: String): String? = slugByName[name.trim().lowercase()]
}
