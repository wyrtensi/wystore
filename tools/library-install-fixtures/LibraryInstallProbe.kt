package dev.wystore.fixture

import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import dev.wystore.data.*
import dev.wystore.data.library.*
import dev.wystore.data.local.UpdateQueueEntity
import dev.wystore.data.local.WyStoreDatabase
import dev.wystore.root.RootInstaller
import dev.wystore.updates.*
import dev.wystore.updates.model.QueueAction
import dev.wystore.updates.model.QueueState
import java.io.File
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Opt-in runtime checks using locally built, signed APKs, never production applications. */
class LibraryInstallProbe {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val repository get() = StoreRepository(context)
    private val pkg = "dev.wystore.installfixture"
    private val version get() = InstrumentationRegistry.getArguments().getString("version", "1").toInt()

    private fun files(signer: String = "original"): List<File> {
        val directory = File(context.cacheDir, "library_install_probe_${UUID.randomUUID()}").apply { check(mkdirs()) }
        return listOf("base", "split").map { role ->
            val name = "$role-v$version-$signer.apk"
            File(directory, name).also { file -> instrumentation.context.assets.open(name).use { input ->
                file.outputStream().use(input::copyTo)
            } }
        }
    }

    private fun plan(files: List<File>) = requireNotNull(SigningVerifier.verifyArtifacts(context.packageManager, files,
        repository.installedAppFresh(pkg), expectedPackageName = pkg, allowReinstall = true).plan)

    private suspend fun ready(files: List<File>, confirm: Boolean = false): UpdateQueueEntity {
        val plan = plan(files)
        val revision = repository.sourceRevision(pkg)
        val artifacts = files.map { file ->
            val hash = LibraryFileIntegrity.sha256(file)
            LibraryArtifact("$hash.apk", hash, file.length(), "https://github.com/wyrtensi/wystore-library/releases/download/$pkg-library/$hash.apk")
        }
        val members = artifacts.mapIndexed { index, artifact -> LibraryMember(artifact.sha256,
            if (index == 0) "base.apk" else "split_config.en.apk", if (index == 0) "base" else "split", if (index == 0) "" else "config.en") }
        val variant = LibraryVariant("probe", version.toLong(), version.toString(), plan.identity.signingDigests, null,
            LibraryCompatibility(28, emptyList(), emptySet(), 420, "en_US", setOf("en"), emptySet(), 0,
                emptySet(), emptySet(), emptySet()), members, "f".repeat(64))
        fun descriptor(kind: String, hash: String) = LibraryArtifact("$kind-$hash.json", hash, 1,
            "https://github.com/wyrtensi/wystore-library/releases/download/$pkg-library/$kind-$hash.json")
        val app = LibraryApplication(pkg, "Installation fixture", variant.id, listOf(variant), artifacts.associateBy { it.sha256 },
            LibraryPublication("$pkg-library", "https://github.com/wyrtensi/wystore-library/releases/tag/$pkg-library",
                descriptor("catalog", "c".repeat(64)), descriptor("manifest", "d".repeat(64))))
        val queue = QueueRepository.getInstance(context)
        val dao = WyStoreDatabase.getInstance(context).updateQueueDao
        dao.find(pkg, version.toLong(), ManagedSource.WY_STORE.name)?.let { previous ->
            if (dao.getSession(previous.id) != null) {
                InstallSessionRecovery.recover(context, previous.id)
                check(InstallSessionWriter(context).abandon(previous.id)) { "The previous fixture session is still running" }
            }
            queue.discard(previous.id)
        }
        queue.enqueueReadyUpdate(plan, app.name, ManagedSource.WY_STORE, sourceRevision = revision,
            sourceHashes = artifacts.map { it.sha256 }, libraryRequestJson = LibraryRequestCodec.encode(app, variant.id, revision))
        val row = requireNotNull(dao.find(pkg, version.toLong(), ManagedSource.WY_STORE.name))
        val manual = row.copy(priority = 10, requiresUserConfirmation = confirm)
        dao.update(manual)
        return manual
    }

    @Test fun installCompleteSetThroughPackageInstaller() = runBlocking {
        ActivityScenario.launch(ComponentActivity::class.java).use {
            val files = files()
            val queue = QueueRepository.getInstance(context)
            val dao = WyStoreDatabase.getInstance(context).updateQueueDao
            val row = ready(files, confirm = true)
            queue.transition(row.id, QueueAction.RequestInstallConfirmation)
            val writer = InstallSessionWriter(context)
            val prepared = writer.prepare(row.id) as PreparedInstall.Session
            queue.transition(row.id, QueueAction.StartInstall)
            writer.commitSession(prepared)
            val deadline = System.currentTimeMillis() + 45_000
            while (System.currentTimeMillis() < deadline &&
                !(repository.installedAppFresh(pkg)?.versionCode == version.toLong() && dao.getById(row.id)?.state == QueueState.INSTALLED.name)) {
                delay(250)
            }
            assertInstalledCompleteSet()
            assertEquals(QueueState.INSTALLED.name, dao.getById(row.id)?.state)
            assertEquals(ManagedSource.WY_STORE, repository.managedApps().first { it.packageName == pkg }.source)
        }
    }

    @Test fun installCompleteSetThroughRoot() = runBlocking {
        val installer = RootInstaller()
        assertTrue("The disposable emulator must grant genuine Magisk root", installer.isAvailable())
        val queue = QueueRepository.getInstance(context)
        val row = ready(files())
        queue.transition(row.id, QueueAction.RequestInstallConfirmation)
        val claim = requireNotNull(RootInstallJournal.claim(context, row.id))
        try {
            val result = installer.install(plan(queue.verifiedArtifactFilesFor(row.id)), update = repository.installedAppFresh(pkg) != null,
                installerPackageName = context.packageName, onSessionCreated = claim::sessionCreated, beforeCommit = claim::beforeCommit)
            assertTrue(result.output, result.success)
            assertTrue(InstalledAppRegistrar.register(context, row))
            queue.reconcileInstallResult(row.id, success = true)
        } finally {
            claim.close()
            InstallSessionRecovery.recover(context, row.id)
        }
        assertInstalledCompleteSet()
    }

    @Test fun anotherSignerCannotProduceAnUpdatePlan() {
        assertNotNull(repository.installedAppFresh(pkg))
        val checked = SigningVerifier.verifyArtifacts(context.packageManager, files("different"),
            repository.installedAppFresh(pkg), expectedPackageName = pkg, allowReinstall = true)
        assertEquals(VerificationError.SIGNATURE_MISMATCH, checked.error)
        assertNull(checked.plan)
    }

    @Test fun missingMemberCannotPrepareAPartialSession() = runBlocking {
        val files = files()
        val queue = QueueRepository.getInstance(context)
        val dao = WyStoreDatabase.getInstance(context).updateQueueDao
        val row = ready(files)
        val stored = dao.getArtifactsForQueue(row.id)
        assertEquals(2, stored.size)
        check(File(stored.last().path).delete())
        var rejected = false
        try { InstallSessionWriter(context).prepare(row.id) }
        catch (error: TransferFailure) { rejected = error.code == dev.wystore.updates.model.QueueErrorCode.INTEGRITY }
        assertTrue("Missing split must fail before creating an Android session", rejected)
        assertNull(dao.getSession(row.id))
        assertInstalledCompleteSet()
    }

    @Test fun sameSizeCorruptionIsRejectedBeforeAnAndroidSessionIsCreated() = runBlocking {
        val row = ready(files())
        val dao = WyStoreDatabase.getInstance(context).updateQueueDao
        val artifact = dao.getArtifactsForQueue(row.id).last()
        val file = File(artifact.path)
        val changed = file.readBytes().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        file.writeBytes(changed)
        assertEquals(artifact.size, file.length())
        var rejected = false
        try { InstallSessionWriter(context).prepare(row.id) }
        catch (error: TransferFailure) { rejected = error.code == dev.wystore.updates.model.QueueErrorCode.INTEGRITY }
        assertTrue(rejected)
        assertNull(dao.getSession(row.id))
        assertInstalledCompleteSet()
    }

    @Test fun leaveRootClaimForProcessRestart() = runBlocking {
        val row = ready(files())
        val queue = QueueRepository.getInstance(context)
        queue.transition(row.id, QueueAction.RequestInstallConfirmation)
        requireNotNull(RootInstallJournal.claim(context, row.id))
        persistRecovery(row, QueueState.READY_TO_INSTALL)
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    @Test fun leaveRootCommitGapForProcessRestart(): Unit = runBlocking {
        val row = ready(files())
        val queue = QueueRepository.getInstance(context)
        queue.transition(row.id, QueueAction.RequestInstallConfirmation)
        val claim = requireNotNull(RootInstallJournal.claim(context, row.id))
        persistRecovery(row, QueueState.READY_TO_INSTALL)
        RootInstaller().install(plan(queue.verifiedArtifactFilesFor(row.id)), true, context.packageName,
            onSessionCreated = claim::sessionCreated, beforeCommit = { id ->
                claim.beforeCommit(id)
                android.os.Process.killProcess(android.os.Process.myPid())
            })
        error("Process restart probe did not terminate")
    }

    @Test fun leaveCompletedRootInstallWithoutItsCallback() = runBlocking {
        val row = ready(files())
        val queue = QueueRepository.getInstance(context)
        queue.transition(row.id, QueueAction.RequestInstallConfirmation)
        val claim = requireNotNull(RootInstallJournal.claim(context, row.id))
        val result = RootInstaller().install(plan(queue.verifiedArtifactFilesFor(row.id)), true, context.packageName,
            onSessionCreated = claim::sessionCreated, beforeCommit = claim::beforeCommit)
        assertTrue(result.output, result.success)
        persistRecovery(row, QueueState.INSTALLED)
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    @Test fun leaveSessionCommitGapForProcessRestart() = runBlocking {
        val row = ready(files())
        val queue = QueueRepository.getInstance(context)
        queue.transition(row.id, QueueAction.RequestInstallConfirmation)
        InstallSessionWriter(context).prepare(row.id) as PreparedInstall.Session
        queue.transition(row.id, QueueAction.StartInstall)
        WyStoreDatabase.getInstance(context).updateQueueDao.updateSessionState(row.id,
            InstallSessionWriter.SESSION_COMMITTED, System.currentTimeMillis())
        persistRecovery(row, QueueState.READY_TO_INSTALL)
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    @Test fun recoverInterruptedInstallAfterProcessRestart() = runBlocking {
        val preferences = context.getSharedPreferences("library_install_probe", 0)
        val id = requireNotNull(preferences.getString("recovery_row", null))
        val dao = WyStoreDatabase.getInstance(context).updateQueueDao
        InstallSessionRecovery.recover(context, id)
        assertEquals(preferences.getString("expected_state", null), dao.getById(id)?.state)
        assertNull(dao.getSession(id))
        assertInstalledCompleteSet()
    }

    private suspend fun persistRecovery(row: UpdateQueueEntity, expected: QueueState) {
        assertEquals(SourceSwitchResult.InstallInProgress, SourceSelectionCoordinator(context).select(pkg,
            AppSourceChoice.GitHub(GitHubRepository("example", "blocked"), pkg)))
        check(context.getSharedPreferences("library_install_probe", 0).edit()
            .putString("recovery_row", row.id).putString("expected_state", expected.name).commit())
    }

    private fun assertInstalledCompleteSet() {
        val installed = repository.installedAppFresh(pkg)
        assertEquals(version.toLong(), installed?.versionCode)
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(pkg, PackageManager.GET_META_DATA)
        assertEquals(listOf("config.en"), info.splitNames?.toList())
        assertEquals(context.packageName, installed?.installerPackageName)
    }
}
