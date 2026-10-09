#!/usr/bin/env python3
"""Install a list of apps through Wy Store over adb, without root.

Wy Store fetches each package from RuStore and verifies it, as it does for its own queue. This
script then installs the verified files silently through a pm session, which names Wy Store as
the installer. Afterwards the apps are Wy Store's managed apps and get their updates from it.

    python tools/wystore_adb_install.py ru.package.one ru.package.two
    python tools/wystore_adb_install.py -s <serial> --timeout 1800 ru.package.one

Exit code 0 means every package was installed and confirmed by Wy Store. Design:
docs/plans/2026-10-09-adb-agent-install.md.
"""
import argparse
import json
import shlex
import subprocess
import sys
import time

AUTHORITY = "content://app.wystore.agent"
ACTIVITY = "app.wystore/dev.wystore.MainActivity"
FAILED_STATES = {"FAILED", "CANCELED", "SKIPPED"}


class Adb:
    def __init__(self, serial):
        self.base = ["adb"] + (["-s", serial] if serial else [])

    def shell(self, command):
        result = subprocess.run(self.base + ["shell", command], capture_output=True, text=True)
        return (result.stdout + result.stderr).strip()

    def call(self, method, arg=None):
        command = f"content call --uri {AUTHORITY} --method {method}"
        if arg:
            command += f" --arg {shlex.quote(arg)}"
        out = self.shell(command)
        # Result: Bundle[{json={...}}]
        if "json=" not in out:
            raise RuntimeError(f"Wy Store did not answer {method}: {out}")
        return json.loads(out[out.index("json=") + 5: out.rindex("}]")])


def install_row(adb, row):
    artifacts = row["artifacts"]
    total = sum(a["size"] for a in artifacts)
    created = adb.shell(f"pm install-create -r -i app.wystore -S {total}")
    if "[" not in created:
        return False, created
    session = created[created.index("[") + 1: created.index("]")]
    for index, artifact in enumerate(artifacts):
        written = adb.shell(
            f"content read --uri {artifact['uri']} | pm install-write -S {artifact['size']} {session} {index}.apk -"
        )
        if "Success" not in written:
            adb.shell(f"pm install-abandon {session}")
            return False, written
    committed = adb.shell(f"pm install-commit {session}")
    if "Success" not in committed:
        return False, committed
    return True, committed


def hint(message):
    if "INSTALL_FAILED_USER_RESTRICTED" in message:
        return (" - on Xiaomi turn on Developer options > 'USB debugging (Security settings)'"
                " and allow 'Install via USB'")
    return ""


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("packages", nargs="+")
    parser.add_argument("-s", "--serial", help="adb device serial")
    parser.add_argument("--timeout", type=int, default=1800, help="seconds to wait for downloads")
    args = parser.parse_args()
    # Progress and errors go to two streams; keep them in the order they happened.
    sys.stdout.reconfigure(line_buffering=True)
    adb = Adb(args.serial)

    # A user-initiated transfer may only be scheduled by an app that is on screen.
    adb.shell(f"am start -n {ACTIVITY}")
    time.sleep(2)

    answer = adb.call("install", ",".join(args.packages))
    for name in answer.get("invalid", []):
        print(f"{name}: not a package name", file=sys.stderr)
    for name in answer.get("overLimit", []):
        print(f"{name}: over the per-call limit, not queued", file=sys.stderr)
    pending = {}
    failed = []
    for item in answer.get("queued", []):
        if "id" in item:
            pending[item["id"]] = item["package"]
            print(f"{item['package']}: queued ({item['id']})")
        else:
            failed.append(item["package"])
            print(f"{item['package']}: {item.get('error', 'not queued')}", file=sys.stderr)

    done = []
    deadline = time.time() + args.timeout
    while pending and time.time() < deadline:
        status = adb.call("status", ",".join(pending))
        for row in status["rows"]:
            name, state = row["package"], row["state"]
            if state == "READY_TO_INSTALL" and row["artifacts"]:
                ok, message = install_row(adb, row)
                if not ok:
                    print(f"{name}: install failed: {message}{hint(message)}", file=sys.stderr)
                    failed.append(name)
                else:
                    confirmed = adb.call("installed", row["id"])
                    if confirmed.get("confirmed"):
                        print(f"{name}: installed {row['versionName']}, Wy Store is its installer")
                        done.append(name)
                    else:
                        print(f"{name}: installed, but Wy Store refused it: {confirmed.get('reason')}", file=sys.stderr)
                        failed.append(name)
                pending.pop(row["id"], None)
            elif state == "INSTALLED":
                print(f"{name}: already installed by Wy Store")
                done.append(name)
                pending.pop(row["id"], None)
            elif state in FAILED_STATES:
                print(f"{name}: {state} {row.get('error', '')} {row.get('detail', '')}".rstrip(), file=sys.stderr)
                failed.append(name)
                pending.pop(row["id"], None)
        missing = set(pending) - {row["id"] for row in status["rows"]}
        for queue_id in missing:
            print(f"{pending.pop(queue_id)}: removed from the queue in the app", file=sys.stderr)
        if pending:
            time.sleep(3)

    for name in pending.values():
        print(f"{name}: timed out waiting for the download", file=sys.stderr)
        failed.append(name)
    print(f"installed {len(done)}, failed {len(failed)}")
    return 0 if not failed else 1


if __name__ == "__main__":
    sys.exit(main())
