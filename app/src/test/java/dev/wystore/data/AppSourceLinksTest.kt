package dev.wystore.data

import org.junit.Assert.*
import org.junit.Test

class AppSourceLinksTest {
    @Test fun `library links to its own stable application release while RuStore keeps its listing`() {
        assertEquals("https://github.com/wyrtensi/wystore-library/releases/tag/org.example.app-library",
            AppSourceLinks.page(ManagedSource.WY_STORE, "org.example.app"))
        assertEquals("https://www.rustore.ru/catalog/app/org.example.app",
            AppSourceLinks.page(ManagedSource.RUSTORE, "org.example.app"))
    }
}
