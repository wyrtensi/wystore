package dev.wystore

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.wystore.background.NotificationIntentFactory
import dev.wystore.data.StoreRepository
import dev.wystore.localization.AppLocaleController
import dev.wystore.permissions.NotificationPermissionPolicy
import dev.wystore.permissions.NotificationPermissionStore
import dev.wystore.permissions.PermissionRepository
import dev.wystore.localization.VerificationTextResolver
import dev.wystore.settings.SettingsRepository
import dev.wystore.settings.toAppSettings
import dev.wystore.ui.WyStoreApp
import dev.wystore.ui.theme.WyStoreTheme
import dev.wystore.data.SigningVerifier
import dev.wystore.updates.UserConfirmedInstaller
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {
    private val storeViewModel by viewModels<StoreViewModel>()
    private var packageWaitingForInstallPermission: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Keeps the synchronous settings cache fresh so workers and startup never block on DataStore.
        SettingsRepository(this).warmUp(lifecycleScope)
        val settings = StoreRepository(this).settings()
        AppLocaleController.applyOnStartup(settings.language)
        setContent {
            val state by storeViewModel.state.collectAsState()
            AskForNotificationsOnce(
                hasSomethingToReportAbout = state.managed.isNotEmpty() ||
                    state.installQueue.isNotEmpty()
            )
            WyStoreTheme(settings = state.settings.toAppSettings()) {
                // Not ::beginPendingInstall. A tap goes into the queue, and the queue hands one
                // package at a time to the collector below - Android shows one confirmation dialog
                // at a time, so firing several at once lost all but one of them.
                WyStoreApp(storeViewModel, storeViewModel::requestInstall)
            }
        }
        handleNotificationDeepLink(intent)

        // Installing needs an Activity for Android's confirmation dialog, so the ViewModel asks for
        // one install at a time and this carries it out. It is what lets "update all" walk the
        // whole list instead of stopping after the first APK.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                storeViewModel.installRequest.collect { packageName ->
                    if (packageName == null) return@collect
                    storeViewModel.consumeInstallRequest()
                    beginPendingInstall(packageName)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationDeepLink(intent)
    }

    /**
     * Keeps the library in step with the device while the app is open.
     *
     * `PACKAGE_ADDED` and friends are implicit broadcasts, and manifest-declared receivers for them
     * do not fire on API 26+. A receiver registered at runtime does, so this is what tells Wy Store
     * that an install or uninstall just happened — including its own installs, whose package the
     * cached `getInstalledPackages` list does not yet contain.
     */
    private val packageChangeReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // The cached package list is exactly what this broadcast invalidates.
            dev.wystore.data.invalidateInstalledApps()
            storeViewModel.refreshLibrary()
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(
            this,
            packageChangeReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        runCatching { unregisterReceiver(packageChangeReceiver) }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        storeViewModel.refreshLibrary(reportConfirmed = true)
        if (!packageManager.canRequestPackageInstalls()) return
        // The in-memory field only survives while this Activity does; the durable queue is what
        // carries the intent to install across the trip to Android Settings and a possible kill.
        lifecycleScope.launch {
            val resumed = buildSet {
                packageWaitingForInstallPermission?.let(::add)
                addAll(storeViewModel.packagesAwaitingUnknownSources())
            }
            packageWaitingForInstallPermission = null
            for (packageName in resumed) {
                storeViewModel.clearAwaitingUnknownSources(packageName)
            }
            storeViewModel.refreshPendingUpdates()
            resumed.firstOrNull()?.let(::beginPendingInstall)
        }
    }

    private fun beginPendingInstall(packageName: String) {
        val update = storeViewModel.pendingUpdate(packageName) ?: run {
            storeViewModel.reportInstallLaunchFailure(getString(R.string.msg_install_artifact_missing))
            return
        }
        if (!packageManager.canRequestPackageInstalls()) {
            packageWaitingForInstallPermission = packageName
            storeViewModel.reportInstallPermissionRequired(packageName)
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${this.packageName}")))
            return
        }
        lifecycleScope.launch {
            // Reading and hashing every APK in the set is disk plus zip work; on the main thread it
            // stalls the frame that reacted to the install tap.
            val verification = withContext(Dispatchers.IO) {
                val installed = storeViewModel.installedApp(packageName)
                SigningVerifier.verifyArtifacts(
                    packageManager = packageManager,
                    files = update.filePaths.map(::File),
                    installed = installed,
                    expectedPackageName = update.packageName,
                    // See InstallSessionWriter: a reinstall the user asked for is not a downgrade.
                    allowReinstall = update.userRequested
                )
            }
            if (!verification.isValid) {
                storeViewModel.reportInstallLaunchFailure(
                    verification.error?.let { getString(VerificationTextResolver.stringRes(it)) }
                        ?: getString(R.string.msg_install_reverify_failed)
                )
                return@launch
            }
            if (update.signingDigests.isEmpty()) {
                // Written by a build that dropped the verified identity. The bytes on disk cannot be
                // tied back to what was checked at download time, so the item is discarded and the
                // user is asked to download it again rather than installing something unverified.
                storeViewModel.discardUnverifiablePendingUpdate(packageName)
                return@launch
            }
            if (verification.identity.signingDigests != update.signingDigests) {
                storeViewModel.reportInstallLaunchFailure(getString(R.string.msg_install_signature_drift))
                return@launch
            }
            try {
                if (UserConfirmedInstaller(this@MainActivity).install(update)) {
                    storeViewModel.reportInstallStarted(packageName)
                } else {
                    // The single queue slot was busy, so the row went back to waiting. Nothing is
                    // coming for it now, and a queue of installs waiting on it would stop dead.
                    storeViewModel.reportInstallDeferred(packageName)
                }
            } catch (error: Exception) {
                storeViewModel.reportInstallLaunchFailure(
                    error.message ?: getString(R.string.msg_install_launch_failed)
                )
            }
        }
    }

    private fun handleNotificationDeepLink(intent: Intent?) {
        if (intent == null) return
        val destination = intent.getStringExtra(NotificationIntentFactory.EXTRA_DESTINATION)
        val packageName = intent.getStringExtra(NotificationIntentFactory.EXTRA_PACKAGE_NAME)
        // The notification's own button asks for the install itself; only the body of the
        // notification opens the page and waits for the user to press something.
        if (intent.getBooleanExtra(NotificationIntentFactory.EXTRA_START_INSTALL, false)) {
            storeViewModel.requestInstallFromNotification(packageName?.takeIf { it.isNotBlank() })
            return
        }
        if (!packageName.isNullOrBlank() && destination?.startsWith("updates/") == true) {
            storeViewModel.openDetails(packageName)
        }
    }

/**
 * Asks Android for notifications once, and only when there is something to notify about.
 *
 * Never at first launch: the dialog is one-shot - a refusal is final for the life of the install -
 * and a question asked before anyone knows what this app is, is a question likely to be refused.
 * Once the store is actually looking after something, the question has an answer the user can give
 * on evidence: "should this tell you when it finds an update for the apps you just gave it".
 *
 * Below Android 13 nothing happens here at all; there is no permission to ask for, and
 * notifications switched off in Settings are turned back on in Settings.
 */
@Composable
private fun AskForNotificationsOnce(hasSomethingToReportAbout: Boolean) {
    val context = LocalContext.current
    val store = remember(context) { NotificationPermissionStore(context) }
    var asked by rememberSaveable { mutableStateOf(store.asked()) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(hasSomethingToReportAbout, asked) {
        if (asked) return@LaunchedEffect
        val granted = PermissionRepository(context).snapshot().notificationsGranted
        if (!NotificationPermissionPolicy.shouldOfferUnprompted(
                granted = granted,
                alreadyAsked = false,
                hasSomethingToReportAbout = hasSomethingToReportAbout
            )
        ) {
            return@LaunchedEffect
        }
        store.markAsked()
        asked = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
}
