package dev.wystore.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.wystore.R
import dev.wystore.data.InstallSource
import dev.wystore.data.InstalledApp
import dev.wystore.data.OwnershipHandover
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OwnershipTransferDetailsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val installed = InstalledApp("example.transfer", "Transfer app", "3.0", 300,
        1, InstallSource.GOOGLE_PLAY, emptySet())

    @Test fun namesApplicationVersionsAndSelectedFileBeforeAnyDestructiveAction() {
        compose.setContent {
            MaterialTheme {
                OwnershipTransferDetails(installed, "v2.0", OwnershipHandover.REINSTALL,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    assetName = "transfer-v2.0.apk", targetIsReleaseTag = true)
            }
        }
        compose.onNodeWithText(installed.label).assertIsDisplayed()
        compose.onNodeWithText(installed.packageName).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.search_installed_version, "3.0"))
            .assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.ownership_transfer_release, "v2.0"))
            .assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.ownership_transfer_file, "transfer-v2.0.apk"))
            .assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.ownership_transfer_data_loss))
            .performScrollTo().assertIsDisplayed()
        // The caller alone controls the destructive action; this component cannot uninstall.
        compose.onNodeWithText(compose.activity.getString(R.string.dialog_replace_confirm))
            .assertDoesNotExist()
    }

    @Test fun profileCaveatCanBeExpandedWithoutHidingTheDataLossWarning() {
        compose.setContent {
            MaterialTheme {
                OwnershipTransferDetails(installed, "4.0", OwnershipHandover.REINSTALL,
                    modifier = Modifier.verticalScroll(rememberScrollState()), targetVersionCode = 400)
            }
        }
        val caveat = compose.activity.getString(R.string.ownership_transfer_profile_limits)
        compose.onNodeWithText(caveat).assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(R.string.ownership_transfer_more))
            .performScrollTo().performClick()
        compose.onNodeWithText(caveat).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.ownership_transfer_data_loss))
            .performScrollTo().assertIsDisplayed()
    }

    @Test fun unsupportedAndroidExplainsLimitationWithoutAskingToDeleteData() {
        compose.setContent {
            MaterialTheme {
                OwnershipTransferDetails(installed, "4.0", OwnershipHandover.UNSUPPORTED_ANDROID)
            }
        }
        compose.onNodeWithText(compose.activity.getString(R.string.ownership_unsupported_android))
            .assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.ownership_transfer_data_loss))
            .assertDoesNotExist()
    }

    @Test fun adoptionNoticeKeepsDataLossVisibleAndExplainsProfileLimitsOnRequest() {
        compose.setContent {
            MaterialTheme {
                OwnershipHandoverNotice(OwnershipHandover.REINSTALL,
                    Modifier.verticalScroll(rememberScrollState()))
            }
        }
        compose.onNodeWithText(compose.activity.getString(R.string.ownership_transfer_data_loss))
            .assertIsDisplayed()
        val caveat = compose.activity.getString(R.string.ownership_transfer_profile_limits)
        compose.onNodeWithText(caveat).assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(R.string.ownership_transfer_more))
            .performScrollTo().performClick()
        compose.onNodeWithText(caveat).performScrollTo().assertIsDisplayed()
    }
}
