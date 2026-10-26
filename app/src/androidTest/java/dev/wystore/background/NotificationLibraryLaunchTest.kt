package dev.wystore.background

import android.content.Context
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.wystore.MainActivity
import dev.wystore.StoreViewModel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationLibraryLaunchTest {
    @Test
    fun libraryNotificationCanCreateViewModelBeforeFirstComposition() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(NotificationIntentFactory.EXTRA_DESTINATION, NotificationIntentFactory.libraryDestination())
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        // The notification creates the ViewModel synchronously on Main.immediate, before Compose.
        // Its initial confirmation StateFlow must not observe uninitialized queue backing fields.
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[StoreViewModel::class.java].openLibraryFromNotification()
            }
        }
    }
}
