package dev.wystore.data.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryListingTextTest {
    @Test fun `Google Play markup becomes readable text without exposing tags or entities`() {
        assertEquals("Canva: Photo & Video", LibraryListingText.plain("Canva: Photo &amp; Video"))
        assertEquals("First\nSecond", LibraryListingText.plain("First<br>Second"))
        assertEquals("Read more", LibraryListingText.plain("<b>Read</b> <a href='https://example.test'>more</a>"))
    }
}
