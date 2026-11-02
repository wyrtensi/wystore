# Diagnostics over adb

Connect an Android device with USB debugging enabled and select its serial
explicitly. No root or interactive Wy Store screen is required.

```powershell
python tools/wystore_adb_diagnostics.py -s emulator-5554 -o outputs/diagnostics/wystore.txt
```

The helper saves the same UTF-8 report as the in-app diagnostics export. It
includes queue errors, recent failure events, update workers, permissions and
store settings. It does not export Google credentials, account data, private
application files or an unrestricted device logcat.

The underlying read-only endpoint is:

```text
adb -s emulator-5554 exec-out content read --uri content://app.wystore.agent/diagnostics
```

Only adb shell and root UIDs may read this endpoint. Other application UIDs
are rejected. The Python helper preserves the output bytes on Windows; shell
redirection may use a different text encoding.

The installed Wy Store build must contain this endpoint. Older releases
report that the URI is unavailable; use their in-app diagnostics export.
