package dev.wystore.ui.tv

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.ViewModelProvider
import dev.wystore.R
import dev.wystore.StoreViewModel
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TvVoiceResultTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private class SpeechRegistry : ActivityResultRegistry(), ActivityResultRegistryOwner {
        override val activityResultRegistry: ActivityResultRegistry get() = this
        var request: Int? = null
        var launches = 0
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>,
            input: I, options: ActivityOptionsCompat?) { request = requestCode; launches++ }
        fun reply(words: ArrayList<String>) = dispatchResult(requireNotNull(request), android.app.Activity.RESULT_OK,
            Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, words))
    }

    private fun startVoice(registry: SpeechRegistry) {
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry) {
                TvApp(ViewModelProvider(compose.activity)[StoreViewModel::class.java], {})
            }
        }
        compose.onNodeWithText(compose.activity.getString(R.string.nav_search)).performClick()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.tv_search_voice)).performClick()
    }

    @Test fun anEmptyReplyOffersRetryWithoutLosingTheSearchScreen() {
        val registry = SpeechRegistry()
        startVoice(registry)
        compose.runOnIdle { registry.reply(arrayListOf()) }
        compose.onNodeWithText(compose.activity.getString(R.string.common_retry)).assertIsDisplayed().performClick()
        compose.runOnIdle {
            org.junit.Assert.assertEquals(2, registry.launches)
            registry.reply(arrayListOf("Firefox"))
        }
        compose.onNode(hasSetTextAction()).assertTextEquals("Firefox")
    }

    @Test fun anEmptyReplyCanContinueWithKeyboardInput() {
        val registry = SpeechRegistry()
        startVoice(registry)
        compose.runOnIdle { registry.reply(arrayListOf()) }
        compose.onNodeWithText(compose.activity.getString(R.string.tv_search_keyboard)).assertIsDisplayed().performClick()
        compose.onNode(hasSetTextAction()).assertIsFocused().performTextInput("Brave")
        compose.onNode(hasSetTextAction()).assertTextEquals("Brave")
    }

    @Test fun recognitionReturnsToSearchEvenIfTheSearchDestinationWasLeft() {
        var request: Int? = null
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>,
                input: I, options: ActivityOptionsCompat?) { request = requestCode }
        }
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = registry
        }
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                TvApp(ViewModelProvider(compose.activity)[StoreViewModel::class.java], {})
            }
        }
        val context = compose.activity
        compose.onNodeWithText(context.getString(R.string.nav_search)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.tv_search_voice)).performClick()
        compose.runOnIdle { assertTrue(request != null) }
        compose.onNodeWithText(context.getString(R.string.nav_home)).performClick()
        compose.runOnIdle {
            registry.dispatchResult(requireNotNull(request), android.app.Activity.RESULT_OK,
                Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, arrayListOf("", "Firefox")))
        }
        compose.onNode(hasSetTextAction()).assertTextEquals("Firefox")
    }
}
