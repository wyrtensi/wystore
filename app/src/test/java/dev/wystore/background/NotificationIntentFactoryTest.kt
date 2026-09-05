package dev.wystore.background

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationIntentFactoryTest {

    @Test
    fun readyDestinationFormatsCorrectly() {
        assertEquals(
            "updates/dev.wystore.testapp",
            NotificationIntentFactory.readyDestination("dev.wystore.testapp")
        )
    }

    @Test
    fun summaryDestinationIsUpdates() {
        assertEquals(
            "updates",
            NotificationIntentFactory.summaryDestination()
        )
    }

    @Test
    fun extrasConstantsAreStable() {
        assertEquals("extra_destination", NotificationIntentFactory.EXTRA_DESTINATION)
        assertEquals("extra_package_name", NotificationIntentFactory.EXTRA_PACKAGE_NAME)
        assertEquals("extra_queue_id", NotificationIntentFactory.EXTRA_QUEUE_ID)
    }
}
