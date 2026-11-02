package dev.wystore.ui.tv

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import dev.wystore.InstallQueueItem
import dev.wystore.InstallQueueStatus
import dev.wystore.R
import dev.wystore.settings.AppSettings
import dev.wystore.ui.theme.WyStoreTheme
import org.junit.Rule
import org.junit.Test

class TvMyAppsFocusTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun cancellingFirstQueueRowMovesFocusToNextCandidate() {
        val rows = listOf(
            InstallQueueItem(id = "first", packageName = "example.first", label = "First", status = InstallQueueStatus.READY),
            InstallQueueItem(id = "second", packageName = "example.second", label = "Second", status = InstallQueueStatus.READY)
        )
        val actions = TvPackageActions({}, {}, {}, {}, {}, {}, {})
        compose.setContent {
            var queue by remember { mutableStateOf(rows) }
            WyStoreTheme(AppSettings(), tv = true) {
                TvMyAppsScreen(
                    packages = TvPackageContext(emptyList(), emptyList(), queue, emptyList()),
                    packageIcons = emptyMap(), checking = false,
                    firstFocus = remember { FocusRequester() }, takeFocus = true,
                    actions = actions, onCheckUpdates = {}, onUpdateAll = {},
                    onCancel = { id -> queue = queue.filterNot { it.id == id } }, onOpenManaged = {}
                )
            }
        }
        val cancel = compose.activity.getString(R.string.common_cancel)
        compose.onAllNodesWithText(cancel)[0].performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onAllNodesWithText(cancel)[0].assertIsFocused().performClick()
        compose.onNodeWithText(cancel).assertIsFocused()
    }

    @Test fun cancellingLastQueueRowMovesFocusToPreviousCandidate() {
        showQueue(listOf("first", "second"))
        val cancel = compose.activity.getString(R.string.common_cancel)
        compose.onAllNodesWithText(cancel)[1].performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onAllNodesWithText(cancel)[1].assertIsFocused().performClick()
        compose.onNodeWithText(cancel).assertIsFocused()
    }

    @Test fun cancellingMiddleQueueRowMovesFocusToNextCandidate() {
        showQueue(listOf("first", "second", "third"))
        val cancel = compose.activity.getString(R.string.common_cancel)
        compose.onAllNodesWithText(cancel)[1].performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onAllNodesWithText(cancel)[1].assertIsFocused().performClick()
        compose.onAllNodesWithText(cancel)[1].assertIsFocused()
    }

    @Test fun cancellingOnlyQueueRowMovesFocusToCheckAction() {
        showQueue(listOf("first"))
        val cancel = compose.activity.getString(R.string.common_cancel)
        compose.onNodeWithText(cancel).performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithText(cancel).assertIsFocused().performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.updates_check_now)).assertIsFocused()
    }

    @Test fun queueChangesWhileMenuIsFocusedDoNotTakeFocus() {
        val queue = mutableStateOf(rows("first", "second"))
        val takeFocus = mutableStateOf(true)
        val actions = TvPackageActions({}, {}, {}, {}, {}, {}, {})
        compose.setContent {
            WyStoreTheme(AppSettings(), tv = true) {
                Column {
                    Button(onClick = { takeFocus.value = false }) { Text("Menu fixture") }
                    TvMyAppsScreen(
                        TvPackageContext(emptyList(), emptyList(), queue.value, emptyList()),
                        emptyMap(), false, remember { FocusRequester() }, takeFocus.value, actions,
                        onCheckUpdates = {}, onUpdateAll = {}, onCancel = {}, onOpenManaged = {}
                    )
                }
            }
        }
        val cancel = compose.activity.getString(R.string.common_cancel)
        compose.onAllNodesWithText(cancel)[0].performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithText("Menu fixture").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithText("Menu fixture").performClick()
        compose.runOnIdle { queue.value = queue.value.drop(1) }
        compose.onNodeWithText("Menu fixture").assertIsFocused()
    }

    @Test fun externallyRemovedFocusedRowMovesFocusToNextCandidate() {
        val queue = mutableStateOf(rows("first", "second", "third"))
        val actions = TvPackageActions({}, {}, {}, {}, {}, {}, {})
        compose.setContent {
            WyStoreTheme(AppSettings(), tv = true) {
                TvMyAppsScreen(
                    TvPackageContext(emptyList(), emptyList(), queue.value, emptyList()),
                    emptyMap(), false, remember { FocusRequester() }, true, actions,
                    onCheckUpdates = {}, onUpdateAll = {}, onCancel = {}, onOpenManaged = {}
                )
            }
        }
        val cancel = compose.activity.getString(R.string.common_cancel)
        compose.onAllNodesWithText(cancel)[1].performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onAllNodesWithText(cancel)[1].assertIsFocused()
        compose.runOnIdle { queue.value = queue.value.filterNot { it.id == "second" } }
        compose.onAllNodesWithText(cancel)[1].assertIsFocused()
    }

    private fun rows(vararg ids: String) = ids.map {
        InstallQueueItem(id = it, packageName = "example.$it", label = it, status = InstallQueueStatus.READY)
    }

    private fun showQueue(ids: List<String>) {
        val actions = TvPackageActions({}, {}, {}, {}, {}, {}, {})
        compose.setContent {
            var queue by remember { mutableStateOf(rows(*ids.toTypedArray())) }
            WyStoreTheme(AppSettings(), tv = true) {
                TvMyAppsScreen(
                    TvPackageContext(emptyList(), emptyList(), queue, emptyList()),
                    emptyMap(), false, remember { FocusRequester() }, true, actions,
                    onCheckUpdates = {}, onUpdateAll = {},
                    onCancel = { id -> queue = queue.filterNot { it.id == id } }, onOpenManaged = {}
                )
            }
        }
    }
}
