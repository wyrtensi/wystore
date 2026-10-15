---
name: wystore-device
description: Check a Wy Store change on the user's real Android phone over adb - install a build without losing data, open screens, tap by text, take screenshots, imitate the compact phone, and check what RuStore answers for a package. Use when asked to verify on the phone, look at a screen, reproduce a UI bug, or when the user says the phone is connected over adb.
---

# Wy Store on the real phone

The phone is the user's own. Its library, accepted apps, queue and settings are real data, and the
user may be using it while you work. Every rule below follows from that.

## Rules

- **Never wipe it.** Install only with `adb install -r` of a build signed with the release key
  (`python tools/verify.py --release` builds and checks one). Never run `connectedAndroidTest`,
  `pm clear`, `adb uninstall app.wystore` or a debug build over the release one: each of them deletes
  the accepted-apps list, and an uninstall also makes Android forget Wy Store as the installer of
  every app it installed.
- **Look, do not decide.** Reading the screen, opening screens, expanding rows, opening a dialog and
  pressing its Cancel are fine. Anything that changes the user's data needs their explicit yes in
  chat first: Accept / Adopt, Replace, Install, Update, Remove, Uninstall, Skip, Download, Pause,
  answering a system install prompt, changing a setting. If a test genuinely needs one (for example
  re-accepting an app to see a button), say what you will change and how you will put it back, and
  check afterwards that it is back.
- **Check the screen before every tap.** The user may navigate at the same time, and a dialog may
  appear (Wy Store offers the next pending install on launch). Use `tap:REGEX` steps, which fail
  with "not found" instead of tapping coordinates, and `expect:REGEX` before a sequence. If the
  screen is not what you expected, stop and look; do not retry blindly.
- **If the user is clearly using the phone, stop driving it** and say so.
- **Put everything back:** font scale, density, files you pushed to `/sdcard`.

## Tools

`tools/adb_ui.py` (run `python tools/adb_ui.py -h`):

```
python tools/adb_ui.py launch sleep:3 "tap:^Library$" sleep:2 texts
python tools/adb_ui.py "expect:^Not accepted" "tap:^Select$" shot:selection
python tools/adb_ui.py -s <serial> ...      # when more than one device is attached
```

- `texts` prints every visible text with its bounds - prefer it to screenshots for checking wording.
- `shot:NAME` writes `build/adb-shots/NAME.png`; open the image to look at layout. Screenshots
  of 1220x2712 are scaled when viewed; coordinates in the image are not device coordinates.
- Typing into the search field: `"tap:^Name or package name$" type:amnezia back` (Back hides the
  keyboard; a tap on the keyboard can open its clipboard panel).
- `adb` is found through `ANDROID_HOME` or `sdk.dir` in `local.properties`; it is usually not on
  PATH.

Install a build:

```
python tools/verify.py --release
adb install -r app/build/outputs/apk/release/app-release.apk
python tools/adb_ui.py launch
```

The app interface on the phone may be in English; text is matched in whatever language it shows.

## Shell traps on this Windows machine

- Git Bash rewrites `/sdcard/...` arguments into Windows paths. Prefix adb commands with
  `MSYS_NO_PATHCONV=1`, or go through `tools/adb_ui.py`, which passes argv directly.
- PowerShell `>` re-encodes binary output: `adb exec-out screencap -p > x.png` gives a broken PNG.
- Piping a Python script to `head` on Windows ends it with `OSError: [Errno 22]` on the next print;
  that is the pipe closing, not a bug in the script.

## Imitating the compact phone

Layout must fit two phones: a compact one (sw393dp, system font scale 1.15) and a large one. To
check the compact case on the large phone, raise the font scale and read the real numbers first:

```
adb shell dumpsys window displays | grep -E "sw[0-9]+dp|fontScale"
adb shell settings get system font_scale        # remember it
adb shell settings put system font_scale 1.15
... screenshots ...
adb shell settings put system font_scale <the value you read>
```

Do not use `wm density reset`: a phone may carry a density override the user chose, and reset
destroys it. If you change density, put back the exact override you read.

## What a badge or warning is based on

- The library badge is the **installer of record**, from `dumpsys package <pkg>`
  (`installerPackageName`, `initiatingPackageName`, `codePath`). `app.wystore` reads "Wy Store",
  `ru.vk.store` "RuStore", a package installer or adb "Manual", apps on a read-only partition or
  installed by `android` "Preinstalled". Where updates come from is a separate line in the opened row.
- "RuStore does not carry this package" comes from the last update check. Ask RuStore directly
  before calling it a bug:

  ```
  python tools/rustore_probe.py com.yandex.yamb
  ```

  Without the app's headers the API answers 400 for everything; the script sends them. A 404 with
  `android_app_ver` means the page exists but no version is offered.

## Reporting

Say what you checked on which device (`adb devices -l`), with screenshots or `texts` output as
evidence. Say plainly what you did not check and anything on the phone that changed while you were
working that you did not cause.
