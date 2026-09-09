<div align="center">

<img src="docs/banner-en.png" alt="Wy Store" width="760">

**A RuStore alternative without tracking or ads**

[![Download APK](https://img.shields.io/badge/Download%20APK-0B57D0?style=for-the-badge&logo=android&logoColor=white)](https://github.com/wyrtensi/wystore/releases/latest/download/wystore.apk)

[![Latest release](https://img.shields.io/github/v/release/wyrtensi/wystore?style=flat-square&label=release&color=0B57D0)](https://github.com/wyrtensi/wystore/releases/latest)
[![Build](https://img.shields.io/github/actions/workflow/status/wyrtensi/wystore/build.yml?branch=main&style=flat-square&label=build)](https://github.com/wyrtensi/wystore/actions/workflows/build.yml)
[![Downloads](https://img.shields.io/github/downloads/wyrtensi/wystore/total?style=flat-square&label=downloads)](https://github.com/wyrtensi/wystore/releases)
[![Android 9.0+](https://img.shields.io/badge/Android-9.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white)](#requirements)
[![Telegram chat](https://img.shields.io/badge/Telegram-chat-26A5E4?style=flat-square&logo=telegram&logoColor=white)](https://t.me/+_YytpJdDHgQ4OTYy)
[![Licence MIT](https://img.shields.io/github/license/wyrtensi/wystore?style=flat-square&label=licence)](LICENSE)

[Русский](README.md) · [Releases](https://github.com/wyrtensi/wystore/releases) · [Changelog](CHANGELOG.md) · [Privacy](PRIVACY.md) · [Security](SECURITY.md) · [Project statement](DISCLAIMER.md) · [Licence](LICENSE)

</div>

Wy Store is an Android client for the RuStore catalogue. It shows the same apps the rustore.ru site
serves, but downloads and installs them itself through Android's own package installer, and needs
neither an account nor the RuStore client. GitHub releases are the second source, which is how the
list also covers what RuStore does not carry.

The project has no backend, no accounts, no analytics and no ads. The app talks only to
`rustore.ru`, `github.com` and the storage those services serve their files from.

The catalogue is aimed at users in Russia, so most of the content is in Russian; the interface
itself ships in Russian and English.

Wy Store is not affiliated with RuStore, not endorsed by it and does not act on its behalf: RuStore
is a data source here. See [DISCLAIMER.md](DISCLAIMER.md).

| Home | App page | Library |
|---|---|---|
| ![Home](docs/screenshots/en/home.png) | ![App page](docs/screenshots/en/app-page.png) | ![Library](docs/screenshots/en/library.png) |

**[A walkthrough with screenshots](https://telegra.ph/Wy-Store-magazin-prilozhenij-kotoryj-ne-prosit-vojti-v-akkaunt-09-09)** — the app screen by screen (in Russian).

## Features

- **Two sources in one list.** RuStore entries and releases from curated GitHub projects sit side by
  side in search, on the home screen and in sections, each labelled with its source. Any public
  repository can be added by URL.
- **Catalogue sections.** RuStore sections as tiles on the home screen and on a separate all-sections
  page. Settings can switch to Wy Store's own set of sections instead.
- **APK checks before install.** Package name, versionCode, completeness of the split-APK set and the
  signing certificate are verified before the file reaches the installer. A mismatch is a refusal
  with a stated reason.
- **Background updates.** Scheduled checks, automatic download of what they find, and installation
  without a dialog where Android allows it. No root required.
- **Download queue.** Kept in a database, survives reboots and dropped connections, and resumes from
  where it stopped using HTTP Range.
- **Reviews and ratings.** For RuStore apps: the rating, a breakdown by star count and a filter by
  score.
- **Library.** Installed apps with filters by source; update and uninstall from one list.
- **Backup.** Export and import of settings, adopted apps and added repositories as a single JSON
  file.
- **Traffic control.** A Wi-Fi-only mode for background downloads; a manual download over mobile data
  asks for confirmation first.
- **Interface.** Russian and English, light and dark themes, Material You.

## Install

1. Download `wystore-<version>.apk` from the
   [latest release](https://github.com/wyrtensi/wystore/releases/latest).
2. Open the file. Android asks once for permission to install from this source.
3. After that Wy Store updates itself: it watches the releases in this repository.

Every release carries two files, `wystore-<version>.apk` and `wystore.apk`. They are the same build
with the same signature; the second one keeps the permanent
`releases/latest/download/wystore.apk` link working.

The APK is signed with a permanent key whose fingerprint CI prints on every publish. To check the
file you downloaded:

```bash
apksigner verify --print-certs wystore-<version>.apk
```

### Moving to 0.2.0 from an earlier version

Version 0.2.0 changed the application id from `dev.wystore` to `app.wystore`. To Android that is a
different app, so this one update has to be installed by hand:

1. In the old Wy Store: **Settings → Backup and transfer → Export**, save the file.
2. Install the new APK.
3. In the new Wy Store: **Settings → Backup and transfer → Import**, pick that file. Settings,
   adopted apps and added GitHub repositories carry over.
4. Uninstall the old version, or two stores will chase the same updates.
5. Grant the new app the battery optimisation exemption again, and confirm the update handover on
   the first update: both are tied to the application id and do not transfer.

Self-updating works as before from then on.

## How updates work

Android lets whoever installed an app update it without a dialog. Wy Store uses that: what it
installed, it updates itself — downloading, checking the signature and installing, including while
the app is in the background.

Three settings make up the chain, all on by default:

| Setting | What it does |
|---|---|
| Download updates immediately | A found update starts downloading on its own. Background checks respect the Wi-Fi-only mode. |
| Install right after downloading | A downloaded update goes to the installer without another tap. |
| Update without asking | Skip the system dialog where Android permits it. |

The limits are these:

- A first install is always confirmed — the system dialog cannot be bypassed.
- An app another store installed also asks for confirmation. Its updates can be handed to Wy Store
  from the app's own page: it gets reinstalled, and updates go quietly afterwards.
- Apps installed from Google Play are left alone entirely until that is enabled for a specific app.
- With root the first install goes quiet too — a switch in settings, off by default. The APK checks
  are unchanged.

## Security

- Before an install: package name, versionCode, exactly one base APK in the set, and the signing
  certificate. For an app that is already installed the signature is compared against the installed
  copy and the versionCode has to increase.
- Downloads are restricted to an allowlist of hosts, and redirects are parsed explicitly.
- Downloaded APKs live in the app's private storage and are removed according to the retention
  period and size limit you set.
- A silent install weakens none of the checks: an APK with a foreign signature is refused there too.

Wy Store verifies delivery, not the apps themselves. What an installed program does is its
developer's business, and every page names the source.

Found a hole? [SECURITY.md](SECURITY.md) covers what counts as a vulnerability, where to send it,
and why not to a public issue.

## Privacy

- No account, no registration, no sign-in.
- No analytics, ad SDKs or crash reporters.
- No backend: requests go straight to RuStore and GitHub, with nothing in between.
- The client identifies itself as `WyStore/<version>` and never creates a RuStore `User-Token`.
- Everything the app stores stays on the device. A backup is created on your command and saved
  wherever you point it.

The full text is in [PRIVACY.md](PRIVACY.md).

## Permissions

| Permission | Why |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | Fetching the catalogue and APKs, checking the network type for Wi-Fi-only mode |
| `REQUEST_INSTALL_PACKAGES` | Installing downloaded APKs |
| `UPDATE_PACKAGES_WITHOUT_USER_ACTION`, `ENFORCE_UPDATE_OWNERSHIP` | Updating what Wy Store installed without a dialog |
| `REQUEST_DELETE_PACKAGES` | Uninstalling from the library |
| `QUERY_ALL_PACKAGES` | Matching the catalogue against what is installed: versions, statuses, available updates |
| `POST_NOTIFICATIONS` | Notifications about found updates and download progress |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `RUN_USER_INITIATED_JOBS` | Downloads the system will not kill halfway |
| `RECEIVE_BOOT_COMPLETED` | Restoring the check schedule after a reboot |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Requested optionally: without the exemption background checks arrive when the system feels like it |

## The data source and its limits

The app reads RuStore's public web endpoints — the same thing the site serves a browser. That is not
a supported client API and it can change without notice. The integration is isolated in
`RuStoreSource`, so a change on the source's side shows up as a format error rather than as corrupt
data in the catalogue.

Free apps are supported. Paid apps, in-app purchases and anything requiring a RuStore sign-in are
not.

The endpoints, the reason behind HTTP 419 and the fallback behaviour are described in
[docs/RUSTORE_API_COMPATIBILITY_RU.md](docs/RUSTORE_API_COMPATIBILITY_RU.md) (Russian).

## Requirements

- **Android 9.0 (API 28)** or newer. That is Wy Store's own minimum; apps in the catalogue often
  need something newer, and each app's page states the version it needs before the download rather
  than after.
- Permission to install unknown apps: Android asks for it on the first install.
- Root is optional. Without it, only the silent **first** install is unavailable.
- ABI and screen density are detected automatically, and the matching APK set is chosen.

## Questions

**Is a RuStore account needed?** No. The app neither creates one nor knows how to use one.

**Does Wy Store replace the RuStore client?** For installing and updating free apps, yes. Paid
content and purchases stay with the official client.

**What happens to installed apps if Wy Store is removed?** Nothing: the system installed them and
they stay. Only the automatic updates stop.

**Why does it need the list of installed packages?** To show a real status in the catalogue —
installed, update available, versions match. The list is never sent anywhere.

**Can I add my own GitHub repository?** Yes, by URL in the GitHub section. Releases are picked by
rules: rolling nightly tags and releases without a usable APK are skipped.

## Building

JDK 17 or newer and Android SDK Platform 36.

```bash
./gradlew :app:assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

Tests:

```bash
./gradlew :app:testDebugUnitTest
```

Instrumented tests, with a device or emulator attached:

```bash
./gradlew :app:connectedDebugAndroidTest
```

### Release signing

The signature check applies to Wy Store itself, so a build you make locally will not be updated by a
published release, and vice versa: to Android they are different apps.

Create a key (`keytool` asks for the password itself — do not put it in the command):

```bash
keytool -genkeypair -v -keystore outputs/wystore-release.jks -alias wystore -keyalg RSA -keysize 4096 -validity 10000
```

Signing data comes from `keystore.properties` in the repository root (gitignored; template in
[keystore.properties.example](keystore.properties.example)) or from the environment variables
`WYSTORE_KEYSTORE`, `WYSTORE_KEYSTORE_PASSWORD`, `WYSTORE_KEY_ALIAS`, `WYSTORE_KEY_PASSWORD`. All
four are required: with any of them missing the release build stays unsigned rather than being
signed with the debug key silently.

### Publishing

The entry in [CHANGELOG.md](CHANGELOG.md) under the new version number comes first, then the tag.
The GitHub release text is taken from that section, and a tag without an entry fails the build.

```bash
git tag v0.2.0 && git push origin v0.2.0
```

CI builds the release from the tag — [.github/workflows/release.yml](.github/workflows/release.yml).
The repository secrets must hold `WYSTORE_KEYSTORE_BASE64` (the key file in base64),
`WYSTORE_KEYSTORE_PASSWORD`, `WYSTORE_KEY_ALIAS` and `WYSTORE_KEY_PASSWORD`. The workflow verifies
that the APK is signed and fails if it is not.

The signing key in CI secrets is available to anyone who can change a workflow in this repository.
If that is unacceptable, build releases locally and upload the APK by hand.

## Project layout

| Path | What lives there |
|---|---|
| `app/src/main/java/dev/wystore/data` | Sources, catalogue, models, APK verification |
| `app/src/main/java/dev/wystore/updates` | Download queue, installation, schedulers |
| `app/src/main/java/dev/wystore/background` | Workers, notification and battery policy |
| `app/src/main/java/dev/wystore/selfupdate` | Wy Store's own updates |
| `app/src/main/java/dev/wystore/localization` | Turning typed error codes into text |
| `app/src/main/java/dev/wystore/ui` | Compose screens, theme, shared components |
| `app/src/test` | Unit tests, including HTML fixtures captured from the source |
| `app/src/androidTest` | Instrumented tests |

The source tree is still `dev/wystore`: that is the Java package namespace. The Android application
id is set separately and is `app.wystore`.

## Feedback

Bugs and suggestions go to [Issues](https://github.com/wyrtensi/wystore/issues); discussion happens
in the [Telegram chat](https://t.me/+_YytpJdDHgQ4OTYy).

A bug report is easier to act on with a diagnostics report attached: **Settings → About → Diagnostics
report**. It carries the Android version, the device model, root and permission status, the queue and
the last failures — no accounts, no links, no file paths.

## Licence

MIT, see [LICENSE](LICENSE).
