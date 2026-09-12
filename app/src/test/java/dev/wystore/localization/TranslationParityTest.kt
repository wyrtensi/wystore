package dev.wystore.localization

import dev.wystore.settings.AppLanguage
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What every translation has to hold before it ships.
 *
 * The interface is in six languages and nobody on this project reads all six, so a translation is
 * checked the way the compiler checks code: a missing key is a crash at the moment the screen is
 * drawn, a dropped format argument is an IllegalFormatException in a language nobody runs, and an
 * unescaped apostrophe is a build that fails only once somebody touches resources. All three are
 * cheap to catch here and expensive to find on a user's phone.
 */
class TranslationParityTest {

    private val resDir = File("src/main/res")
    private val base = File(resDir, "values/strings.xml")

    /** The positions the code fills in, "%1${'$'}s" and "%2${'$'}.1f" among them. */
    private val formatToken = Regex("""%(\d+\$)?[-#+ 0,(]*[\d.]*[a-zA-Z]""")

    private fun stringsOf(file: File): Map<String, String> =
        Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(file.readText())
            .associate { it.groupValues[1] to it.groupValues[2] }

    private fun tokensOf(value: String): List<String> =
        formatToken.findAll(value.replace("%%", "")).map { it.value }.sorted().toList()

    private fun translations(): List<Pair<AppLanguage, File>> = AppLanguage.entries
        .filter { it != AppLanguage.SYSTEM && it != AppLanguage.EN }
        .map { it to File(resDir, "values-${it.tag}/strings.xml") }

    @Test
    fun `the test can see the resources it is checking`() {
        // A wrong working directory would otherwise make every check below pass on nothing.
        assertTrue("not found from ${File("").absolutePath}: $base", base.isFile)
    }

    @Test
    fun `every language in the picker has a file and a line in locales_config`() {
        val localesConfig = File(resDir, "xml/locales_config.xml").readText()
        translations().forEach { (language, file) ->
            assertTrue("${language.name}: missing $file", file.isFile)
            assertTrue(
                "${language.name}: not listed in locales_config.xml, so Android's own per-app " +
                    "language picker will not offer it",
                localesConfig.contains("""android:name="${language.tag}"""")
            )
        }
    }

    @Test
    fun `a translation holds the same keys as the base`() {
        val expected = stringsOf(base).keys
        translations().forEach { (language, file) ->
            if (!file.isFile) return@forEach
            val actual = stringsOf(file).keys
            assertEquals(
                "${language.name}: keys missing from the translation",
                emptySet<String>(),
                expected - actual
            )
            assertEquals(
                "${language.name}: keys the base does not have",
                emptySet<String>(),
                actual - expected
            )
        }
    }

    @Test
    fun `a translation fills the same places as the base`() {
        val expected = stringsOf(base)
        translations().forEach { (language, file) ->
            if (!file.isFile) return@forEach
            stringsOf(file).forEach { (key, value) ->
                val want = tokensOf(expected[key] ?: return@forEach)
                assertEquals("${language.name}/$key: format arguments differ", want, tokensOf(value))
            }
        }
    }

    @Test
    fun `no translation carries a bare apostrophe`() {
        val bare = Regex("""(?<!\\)'""")
        (listOf(AppLanguage.EN to base) + translations()).forEach { (language, file) ->
            if (!file.isFile) return@forEach
            stringsOf(file).forEach { (key, value) ->
                assertTrue(
                    "${language.name}/$key: an apostrophe has to be escaped in Android resources",
                    !bare.containsMatchIn(value)
                )
            }
        }
    }
}
