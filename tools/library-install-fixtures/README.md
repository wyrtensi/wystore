# Library installation runtime checks

These opt-in probes build original signed test applications and exercise the real
Wy Store queue, PackageInstaller and root installer. No production APK is modified
or signed. Use disposable emulators only. The fixture package is
`dev.wystore.installfixture`.

Build the fixture files and instrumentation APK:

```powershell
python tools/library-install-fixtures/build.py --output outputs/library-install-fixtures
./gradlew.bat -I tools/library-install-fixtures/fixture.init.gradle :app:assembleDebug :app:assembleDebugAndroidTest
```

Install both debug APKs on the selected disposable emulator. Allow
`REQUEST_INSTALL_PACKAGES` for `app.wystore`. Root probes additionally need a real
superuser implementation granting root to Wy Store, such as Magisk.

For a first install and update with actual system confirmation:

```powershell
python tools/library-install-fixtures/run_session.py --serial emulator-5556 --version 1 --log outputs/library-install-fixtures/session-v1.log
python tools/library-install-fixtures/run_session.py --serial emulator-5556 --version 2 --log outputs/library-install-fixtures/session-v2.log
```

The driver confirms only an enabled Install/Update button in a dialog naming the
installation fixture. It requires a clean `OK (1 test)` result. Place a marker in
the fixture's private `files` directory through `run-as` before updating, then
read it afterward to check data preservation.

Other methods in `dev.wystore.fixture.LibraryInstallProbe` can be selected with
`am instrument -w -e class CLASS#METHOD -e version 2`. Root installation checks
the complete selected base/split set and journals the actual root session.
Negative probes cover another signer, a missing split and same-size corruption.

The `leave...` methods deliberately terminate the application process. A crash
is expected for those commands. Run each one separately, then run
`recoverInterruptedInstallAfterProcessRestart` in a new instrumentation command
and require `OK (1 test)` before any other probe modifies the fixture queue.
This covers a claim before session creation, both commit gaps, and a completed
root installation whose result was not recorded. Do not run the whole probe
class as one suite: JUnit does not guarantee this dependent ordering.

Normal builds and ordinary instrumentation do not include these probes or APKs.
Generated APKs and fixture signing keys stay in the ignored output directory.
