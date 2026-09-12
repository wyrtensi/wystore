package dev.wystore.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RuStoreTextTest {

    /** One backslash. Spelled this way so the sequences under test stay readable below. */
    private val slash = "\\"

    /** The review that showed this: three paragraphs printed as one line with the escapes in it. */
    @Test
    fun `paragraph breaks come back as line breaks`() {
        assertEquals(
            "первый\n\nвторой\n\nтретий",
            RuStoreText.decodeEscapes("первый${slash}n${slash}nвторой${slash}n${slash}nтретий")
        )
    }

    @Test
    fun `text without escapes is returned untouched`() {
        assertEquals("обычный отзыв", RuStoreText.decodeEscapes("обычный отзыв"))
    }

    /** A backslash the author typed must not become the start of an escape on the next pass. */
    @Test
    fun `an escaped backslash stays one backslash`() {
        assertEquals("C:${slash}n", RuStoreText.decodeEscapes("C:$slash${slash}n"))
    }

    @Test
    fun `quotes slashes and tabs are decoded too`() {
        assertEquals(
            "он сказал \"да\"\tи ушёл /",
            RuStoreText.decodeEscapes("он сказал $slash\"да$slash\"${slash}tи ушёл $slash/")
        )
    }

    @Test
    fun `a unicode escape becomes its character`() {
        assertEquals("ё", RuStoreText.decodeEscapes("${slash}u0451"))
    }

    /** Anything this does not recognise is left exactly as it was rather than half-eaten. */
    @Test
    fun `an unknown escape is left alone`() {
        assertEquals("${slash}q", RuStoreText.decodeEscapes("${slash}q"))
        assertEquals(
            "ends with a backslash $slash",
            RuStoreText.decodeEscapes("ends with a backslash $slash")
        )
    }
}
