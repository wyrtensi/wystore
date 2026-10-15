# Wy Store - notes for coding agents

Wy Store is an Android app store client (Kotlin, Jetpack Compose, package `app.wystore`, sources
under `app/src/main/java/dev/wystore`). It installs and updates apps from the RuStore catalogue
and from GitHub releases, takes over apps installed by other stores, and has a phone and a TV
interface.

## Layout

- `StoreViewModel.kt` - app state and most user actions; `ui/navigation/WyStoreRoot.kt` wires
  screens and dialogs to it.
- `data/` - sources (`RuStoreSource`, `GitHubReleaseSource`), models, and the decision logic kept
  as plain objects so it can be unit-tested (`AdoptionPolicy`, `GitHubReleasePolicy`,
  `InstallerNames`, `TakeoverPolicy`, ...).
- `updates/`, `background/` - the install queue, downloads, workers.
- `ui/` - Compose screens by area (`library`, `updates`, `github`, `details`, `tv`, ...).
- `app/src/test` - JVM unit tests. `tools/` - scripts below. `docs/` - the website.

## Conventions

- Code, comments, commit messages: English. User-facing text: Russian first, and every string in
  all eight languages the app ships (`wystore-strings`).
- Commits are made as the repository owner, without assistant attribution or co-author lines.
  Messages explain what was wrong for the user and why, in full sentences, and reference issues
  with `Fixes #N`.
- No emoji in code, comments, commits or docs.
- Comments explain why something is done the way it is - usually what went wrong before.
- New decision logic goes into a small testable object with a unit test, not into a composable.
- Do not push, tag or release unless asked.
- Signing material (`keystore.properties`, `outputs/*.jks`) is local and never committed.

## Before saying something works

Run `python tools/verify.py` (add `--release` before installing anywhere) and quote its result
line. Not run - say so. See `wystore-verify`.

## Devices

The phone attached over adb is the user's own, with real data: install only with `adb install -r`
of a release-signed build, never wipe it, and ask before any action that changes its data.
Destructive or clean-device scenarios run on the emulators. See `wystore-device` and
`wystore-emulator`.

## Tools

| Script | What it does |
|---|---|
| `tools/verify.py` | Unit tests with a clean result count; `--release` builds and checks the signing certificate |
| `tools/adb_ui.py` | Reads the screen, taps by text, types, scrolls, takes screenshots over adb |
| `tools/avd.py` | Lists, starts (waits for boot) and stops the project's emulators |
| `tools/add_string.py` | Adds strings to every language at once, with escaping and checks |
| `tools/rustore_probe.py` | Asks the RuStore API about packages with the app's headers |
| `tools/wystore_adb_install.py` | Installs apps through Wy Store over adb (agent install path) |

## Skills

Task guides in the Agent Skills format live in `.agents/skills/<name>/SKILL.md`. Read the one that
matches the task before starting it.

| Skill | Use it when |
|---|---|
| `wystore-verify` | Before claiming anything is fixed, passing or done; before committing or installing |
| `wystore-device` | Checking a change on the real phone over adb, reproducing a UI problem, imitating the compact phone |
| `wystore-emulator` | Anything that wipes or reinstalls, fresh-install and restore flows, RuStore-installed apps, old Android versions, TV |
| `wystore-strings` | Adding or rewording any text the user sees |
| `wystore-feedback-to-issues` | Turning a chat export or a batch of feedback into GitHub issues |
| `wystore-release` | Cutting a release, only when asked |
