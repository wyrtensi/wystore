---
name: wystore-emulator
description: Run Wy Store scenarios on Android emulators instead of the user's phone - anything that wipes or reinstalls, fresh-install and restore flows, apps installed by another store (RuStore), old Android versions, and the TV interface. Use when a check would change or destroy real data, needs a clean device, a specific Android version, or a TV.
---

# Wy Store on emulators

The user's phone holds real data (see `wystore-device`). Everything destructive happens here.

## Emulators on this machine

```
python tools/avd.py list
```

Prepared AVDs: `wy_api28` ... `wy_api35` (phones, Android 9 to 15), `wy_tv_api30` (Android TV),
`wy_gtv_api34` (Google TV). They boot in about 40 seconds.

```
python tools/avd.py start wy_api34 --headless     # prints the serial, e.g. emulator-5554
python tools/avd.py start wy_api34 --wipe         # factory-fresh data
python tools/avd.py stop                          # all emulators
```

Runs are not saved into the snapshot, so every start begins from the same state. With the phone
also attached, pass the serial everywhere: `adb -s emulator-5554 ...`,
`python tools/adb_ui.py -s emulator-5554 ...`.

Install and look:

```
python tools/verify.py --release
adb -s emulator-5554 install -r app/build/outputs/apk/release/app-release.apk
python tools/adb_ui.py -s emulator-5554 launch sleep:5 texts shot:emu_home
```

Stop the emulators you started when you are done.

## Scenarios that belong here

**Reinstalling Wy Store.** What survives an uninstall is a promise the app makes in the backup card.
1. Install, accept a few apps (install one or two small ones through Wy Store first), save a backup
   copy to a file.
2. `adb uninstall app.wystore`, then check with `adb shell dumpsys package <pkg> | grep installer`
   what Android did to the installer of the apps Wy Store had installed.
3. Install again; My apps should offer to restore from the file; restore and check the list.

**Apps installed by RuStore.** Install the RuStore app (its APK from rustore.ru - ask the user
before downloading it) and an app through it. Check the badge reads RuStore, accept the app in Wy
Store, let Wy Store update it, and check whether Android now records Wy Store as the installer and
whether an update-ownership prompt appears (Android 14+).

**Adoption on Google-installed apps** needs Play Store images (`google_apis_playstore`); the
prepared AVDs are `google_apis`, so a Play-installed app cannot be reproduced on them. Say so
rather than faking it with `pm install -i com.android.vending`, which gives a different signature.

**Old Android.** Silent updates, notification permission and the install session API differ by
version; check behaviour changes on the oldest supported (`wy_api28`) as well as the newest.

**TV.** `wy_tv_api30` / `wy_gtv_api34` run the TV interface. Navigate with the D-pad:
`adb shell input keyevent 19/20/21/22` (up/down/left/right), `23` (select), `4` (back).
Screenshots and `texts` work the same way.

## Notes

- Emulator images are x86_64/x86; an APK with only arm splits will not install. Wy Store itself is
  universal.
- Network on the emulator is the host's; RuStore answers the same as on the phone.
- An emulator is not the compact phone: layout checks at the user's font scale belong on a device
  or an AVD set to the same sw-dp and font scale.
