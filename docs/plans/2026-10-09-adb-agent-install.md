# Installing a list of apps over adb

An agent on a computer hands Wy Store a list of package names over adb. Wy Store fetches and
verifies them exactly as it does for its own queue. The agent installs them silently with
`pm install-create -i app.wystore`. Wy Store is the installer of record, and the apps are its
managed apps afterwards. No root is needed.

Checked on the test phone (Xiaomi, Android 16) before writing this:
- `pm install-create -i app.wystore` run from the shell installs without a dialog.
- `installerPackageName` became `app.wystore`.
- `pm install-write ... -` accepts the APK on stdin.

## Entry point: `AgentProvider`

`content://app.wystore.agent`, exported. Every call and every file open is refused unless the
caller's uid is shell (2000) or root (0), so other apps on the phone cannot use it.

| method      | arg                    | does                                                            |
|-------------|------------------------|-----------------------------------------------------------------|
| `install`   | `pkg1,pkg2,...`        | Queues each package as user-requested (RuStore). Marks the row for adb handoff. |
| `status`    | optional queue ids     | JSON for the handoff rows: state, error, versions, and the artifact list. |
| `installed` | queue id               | Confirms an install the agent just made (see below).            |
| `cancel`    | queue id               | Cancels the row and drops the handoff mark.                     |

`install` details:
- An unknown or invalid package name is reported back, not queued.
- If the package already has a row the queue is working on, that row is adopted.

Every answer is a Bundle with a single `json` string.

Artifacts are served by `openFile` at `content://app.wystore.agent/artifact/<queueId>/<index>`.
They are read-only and come from the verified copies the queue already keeps. The agent streams
them on the phone:

    content read --uri content://app.wystore.agent/artifact/<id>/<n> | pm install-write -S <size> <session> <n>.apk -

## Fetching and verification are unchanged

Download, resume, signature checks, and `saveVerifiedArtifacts` run as they do now. For a row
marked for handoff, the worker stops after `Verified`:
- No root install, no background install, no auto-install request, no "ready" notification.
- The row stays READY_TO_INSTALL, so the single slot is free and the next package downloads.

The marks live in `AdbHandoffStore` (SharedPreferences, a set of queue ids). This needs no Room
migration.

## Wy Store learns about the install

`AdbHandoff.confirm(queueId)` is called from `installed` and from `PackageChangedReceiver` for
marked rows. It accepts the install only when:
- the installed versionCode is at least the row's, and
- the signing digests equal the digests verified at download.

Then it runs what every other install path runs:
- `InstalledAppRegistrar.register` (the app becomes managed, its signature is pinned, and later
  updates come through Wy Store);
- `reconcileInstallResult(success)`, which deletes the stored copy;
- the mark is removed.

A mismatch is reported in the answer and the row is left alone.

## Agent script

`tools/wystore_adb_install.py <pkg>...` (Python, no dependencies) does the following:
1. Brings Wy Store to the front. A user-initiated transfer job may only be scheduled by a
   visible app.
2. Calls `install`.
3. Polls `status`.
4. Installs each READY row through a pm session, then calls `installed`.

It recognises `INSTALL_FAILED_USER_RESTRICTED` and says to turn on "USB debugging (Security
settings)" on Xiaomi.

## Errors

- Source and verification failures show in `status` under the same codes the app's UI uses.
- A failed `pm` commit leaves the row READY. It can be retried, or installed from the app.

## Tests

Unit tests:
- the uid gate;
- package list parsing;
- the confirm rule;
- the status JSON.

On device: a real run of the script with two apps. No release until that run passes.
