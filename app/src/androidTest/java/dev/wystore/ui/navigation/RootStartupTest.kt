package dev.wystore.ui.navigation

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.lifecycle.Lifecycle
import dev.wystore.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Composing the real root through the real Activity. The UI tests that render single screens with
 * hand-supplied state cannot catch wiring faults such as a ViewModel the default factory is unable
 * to construct, which is exactly how a startup crash reached the phone.
 */
@RunWith(AndroidJUnit4::class)
class RootStartupTest {

    @Test
    fun theActivityReachesResumedWithTheRealComposeRoot() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    @Test
    fun theActivitySurvivesRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.recreate()
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }
}
