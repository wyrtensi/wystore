<div align="center">

<img src="docs/logo.png" alt="Wy Store" width="128">

# Wy Store

**An Android store client with no account, no backend and no telemetry**

[Русский](README.md) · [Releases](https://github.com/wyrtensi/wystore/releases) · [Licence](LICENSE)

</div>

It browses the RuStore catalogue, tracks releases of curated GitHub projects, verifies every APK
before it is installed, and keeps installed apps up to date in the background.

The catalogue and the curated sections are aimed at users in Russia, so most of the content the app
displays is in Russian; the interface itself ships in Russian and English.

| Home | App page | Library |
|---|---|---|
| ![Home](docs/screenshots/home.png) | ![App page](docs/screenshots/app-page.png) | ![Library](docs/screenshots/library.png) |

## What it does

**Two sources, one list.** RuStore listings and a built-in catalogue of GitHub projects appear side
by side in search and on the home screen, each labelled with where it came from. Any public GitHub
repository can be added by URL; releases are picked by policy, so rolling nightly tags and releases
without an installable APK are skipped rather than offered.

**Verification before installation.** Every downloaded APK set is checked for a single base APK, a
consistent package name and version code across splits, a readable signature, and — when the app is
already installed — the same signing certificate and a version code that actually moves forward. A
set that fails any of these is rejected with a reason, not installed.

**A queue that survives a reboot.** The download queue lives in a database, not in memory. A
download interrupted by a reboot, a killed process or a lost network resumes from where it stopped
with an HTTP range request rather than starting over, and a failure is recorded with a typed reason
instead of an exception message.

**Installation on your terms.** By default every install goes through Android's own confirmation
dialog. On a rooted device silent installation is available, but it is off unless you turn it on and
root is actually granted.

**Notifications that stay quiet.** At most one notification per category. Twenty updates produce one
entry listing them, not twenty; a repeated background check that finds the same updates re-posts
silently instead of alerting again; and quiet hours keep everything soundless inside a window you
choose.

**Background work that respects the battery.** Periodic checks run in a flexible window so the
system can batch them with other wake-ups, stand down under battery saver, and are not scheduled at
all while nothing has been adopted.

**It updates itself.** Wy Store checks its own releases in this repository and installs them through
the same queue, with the same signature check, as any other app.

## Security model

- Installation starts only from an explicit press of an install or update button.
- Package name, version code, APK-set completeness and signing certificate are checked before every
  install. A signature that does not match the installed copy is a refusal, never a warning.
- Downloads are restricted to an allowlist of hosts, with redirects resolved explicitly rather than
  followed blind.
- Apps installed by Google Play are left alone until the per-app override is switched on.
- Verified APKs stay in the app's private storage until the install is confirmed, and are pruned
  under retention and storage limits you control.
- No account, no analytics, no advertising SDK, no server of the project's own.

Wy Store verifies distribution. It does not review the applications themselves, and every install
page names the source so responsibility sits with it.

## Source limitations

The RuStore endpoints the app uses are public web endpoints, not a supported consumer API. The
integration is isolated in `RuStoreSource`, and a change upstream surfaces as a source-format error
rather than as corrupted data. The client uses a bounded `ruStoreVerCode` compatibility fallback and
never creates or copies a RuStore `User-Token`. Free, current-version applications are supported.

The endpoints, the HTTP 419 root cause, the official-APK version conversion and the fallback
behaviour are documented in [docs/RUSTORE_API_COMPATIBILITY_RU.md](docs/RUSTORE_API_COMPATIBILITY_RU.md)
(in Russian).

## Requirements

- Android 8.0 (API 26) or newer
- Permission to install unknown apps, which Android asks for on the first install
- Root is optional and only needed for silent installation

## Build

JDK 17 or newer and Android SDK Platform 36.

```bash
./gradlew :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Tests:

```bash
./gradlew :app:testDebugUnitTest
```

Instrumented tests, with a device or emulator attached:

```bash
./gradlew :app:connectedDebugAndroidTest
```

### Release signing

Releases are signed with a fixed key. Because the signature check applies to Wy Store itself, a build
you compiled locally cannot be updated in place by a published release, and the reverse is also true
— the two are different applications as far as Android is concerned.

Create the key (keytool asks for the password interactively, so it never appears in a command):

```bash
keytool -genkeypair -v -keystore outputs/wystore-release.jks -alias wystore -keyalg RSA -keysize 4096 -validity 10000
```

Signing material is read from `keystore.properties` at the repository root (gitignored; template in
[keystore.properties.example](keystore.properties.example)) or from the environment variables
`WYSTORE_KEYSTORE`, `WYSTORE_KEYSTORE_PASSWORD`, `WYSTORE_KEY_ALIAS` and `WYSTORE_KEY_PASSWORD`.
All four are required: with any of them missing the release build stays unsigned rather than
silently falling back to the debug key.

### Publishing

CI builds a release from a tag, see [.github/workflows/release.yml](.github/workflows/release.yml):

```bash
git tag v0.1.12 && git push origin v0.1.12
```

That needs `WYSTORE_KEYSTORE_BASE64` (the keystore file, base64-encoded), `WYSTORE_KEYSTORE_PASSWORD`,
`WYSTORE_KEY_ALIAS` and `WYSTORE_KEY_PASSWORD` in the repository secrets. The workflow verifies that
the APK really is signed and fails if it is not, so a build nobody could install never gets
published.

A signing key held in CI secrets is reachable by anyone who can change a workflow in this
repository. If that is not acceptable, build the release locally and upload the APK by hand.

## Project layout

| Path | What lives there |
|---|---|
| `app/src/main/java/dev/wystore/data` | Sources, catalogue, models, APK verification |
| `app/src/main/java/dev/wystore/updates` | Durable queue, install flow, schedulers |
| `app/src/main/java/dev/wystore/background` | Workers, notification and battery policy |
| `app/src/main/java/dev/wystore/selfupdate` | Wy Store's own update check |
| `app/src/main/java/dev/wystore/localization` | Typed error codes rendered into text |
| `app/src/main/java/dev/wystore/ui` | Compose screens, theme, shared components |
| `app/src/test` | Unit tests, including HTML fixtures captured from the source |
| `app/src/androidTest` | Instrumented tests |

## Licence

MIT. See [LICENSE](LICENSE).
