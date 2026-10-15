---
name: wystore-verify
description: Prove a Wy Store change works before saying so - run the unit tests with a clean result count, build the signed release APK and check its certificate. Use before claiming anything is fixed, passing or done, before committing, and before installing a build on a device.
---

# Verify before claiming

"Works", "fixed", "passing" are claims that need a command you ran and its output. Not run - say so.

## The command

```
python tools/verify.py                       # unit tests
python tools/verify.py --release             # + assembleRelease + signing certificate check
python tools/verify.py --tests "*AdoptionPolicyTest"
```

It prints `unit tests: N run, F failed, ...` and ends with `VERIFY OK` or `VERIFY FAILED`; exit code
0 only when everything asked for passed. Quote that line when you report.

Why not plain Gradle output: `-q` prints nothing on success, and Gradle leaves the JUnit XML of
earlier runs in place, so counting the results directory after a run that failed to compile reports
the previous run's success. The script deletes the old results first and counts what this run wrote.
A run of 0 tests is a failure.

## What it covers and what it does not

- Unit tests (`:app:testDebugUnitTest`) - the JVM tests under `app/src/test`. Policies and parsers
  live in plain Kotlin objects so they can be tested here; put new decision logic in one and test it
  (see `AdoptionPolicy`, `LibraryGrouping`, `ReleaseNotes`, `InstallerNames`).
- `TranslationParityTest` is part of it: every string must exist in every language.
- `--release` checks the APK is signed with the Wy Store certificate
  (`33dcb500...a258`). Anything else cannot be installed over the published app or over the user's
  phone. It needs `keystore.properties` and the keystore it points to; both are local and gitignored.
- Not covered: Compose UI behaviour, layout on small screens, real installs. Those need a device -
  see the `wystore-device` (real phone, read-mostly) and `wystore-emulator` (anything destructive)
  skills.
- CI additionally runs `:app:lintDebug`; run it when you touched resources or the manifest.

## Order of work

1. Write the failing test first when the change is logic.
2. `python tools/verify.py` until `VERIFY OK`.
3. `python tools/verify.py --release` before installing anywhere or tagging.
4. Check on a device when the change is visible.
5. Report with the numbers. Report failures and skipped steps as plainly as successes.
