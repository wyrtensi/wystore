package dev.wystore.updates

import dev.wystore.data.ManagedSource
import dev.wystore.data.QueueMode
import dev.wystore.updates.model.QueueAction
import dev.wystore.updates.model.QueueItemSnapshot
import dev.wystore.updates.model.QueueState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueCoordinatorTest {

    @Test
    fun smartPromptsModeOffersNextAfterInstall() {
        val secondItem = QueueItemSnapshot(
            id = "item-2",
            packageName = "pkg.two",
            label = "App Two",
            versionName = "1.0",
            versionCode = 20,
            source = ManagedSource.RUSTORE,
            state = QueueState.READY_TO_INSTALL,
            priority = 0,
            position = 1
        )

        // Policy test for smart prompts mode
        val actionSmart = QueueCoordinatorPolicy.determineNextAction(
            mode = QueueMode.SMART_PROMPTS,
            justInstalledId = "item-1",
            nextEligible = secondItem
        )
        assertEquals(QueueAction.OfferNext, actionSmart)

        // Policy test for manual one-by-one mode
        val actionManual = QueueCoordinatorPolicy.determineNextAction(
            mode = QueueMode.MANUAL_ONE_BY_ONE,
            justInstalledId = "item-1",
            nextEligible = secondItem
        )
        assertNull(actionManual)
    }
}
