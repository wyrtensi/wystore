package dev.wystore.localization

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionFailureTest {

    @Test
    fun noNetworkIsAConnectionFailure() {
        assertTrue(SourceTextResolver.isConnectionFailure(UnknownHostException("www.rustore.ru")))
        assertTrue(SourceTextResolver.isConnectionFailure(SocketTimeoutException()))
    }

    @Test
    fun aWrappedOneIsFoundThroughItsCause() {
        assertTrue(SourceTextResolver.isConnectionFailure(IOException("fetch failed", UnknownHostException())))
    }

    @Test
    fun otherFailuresKeepTheirOwnMessage() {
        assertFalse(SourceTextResolver.isConnectionFailure(IOException("HTTP 500")))
        assertFalse(SourceTextResolver.isConnectionFailure(IllegalStateException("bad page")))
    }
}
