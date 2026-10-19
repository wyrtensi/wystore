#!/usr/bin/env python3
"""Drive Wy Store on a phone or an emulator over adb: read the screen, tap by text, screenshot.

    python tools/adb_ui.py texts
    python tools/adb_ui.py "tap:^Apps$" sleep:2 "tap:^Not accepted$" shot:library
    python tools/adb_ui.py -s emulator-5554 launch sleep:3 shot:home

Steps run in order:
    texts             print every visible text / content description with its bounds
    tap:REGEX         tap the first node whose text or description matches REGEX
    long:REGEX        long-press it (starts selection in My apps)
    expect:REGEX      stop with exit code 1 unless something on screen matches REGEX
    type:TEXT         type TEXT into the focused field (spaces become %s)
    top[:N]           scroll to the top (N swipes, default 4)
    down[:PX]         scroll down by about PX pixels (default 1200)
    back              press Back
    launch            start Wy Store
    sleep:SECONDS     wait
    shot:NAME         save a screenshot as <shots dir>/NAME.png and print the path

A tap that finds nothing prints "not found" and the run stops with exit code 1, so a screen that
changed under the script is noticed instead of the next tap landing on whatever is there now.

adb is taken from ANDROID_HOME / ANDROID_SDK_ROOT, then from sdk.dir in local.properties, then
from PATH. Screenshots go to $WYSTORE_SHOTS, or ./build/adb-shots.
"""
import argparse
import os
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PACKAGE = "app.wystore"
DUMP = "/sdcard/wystore_ui.xml"


def find_adb() -> str:
    exe = "adb.exe" if os.name == "nt" else "adb"
    for env in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        if os.environ.get(env):
            candidate = Path(os.environ[env]) / "platform-tools" / exe
            if candidate.exists():
                return str(candidate)
    props = ROOT / "local.properties"
    if props.exists():
        for line in props.read_text(encoding="utf-8").splitlines():
            if line.startswith("sdk.dir="):
                sdk = line.split("=", 1)[1].replace("\\:", ":").replace("\\\\", "\\")
                candidate = Path(sdk) / "platform-tools" / exe
                if candidate.exists():
                    return str(candidate)
    found = shutil.which("adb")
    if found:
        return found
    sys.exit("adb not found: set ANDROID_HOME or sdk.dir in local.properties")


class Device:
    def __init__(self, serial=None):
        self.base = [find_adb()] + (["-s", serial] if serial else [])

    def run(self, *args, binary=False):
        # A list argv, never a shell string: Git Bash rewrites "/sdcard/..." into a Windows path
        # when it sees one on its own command line.
        out = subprocess.run(self.base + list(args), capture_output=True)
        return out.stdout if binary else out.stdout.decode("utf-8", "replace")

    def nodes(self):
        self.run("shell", "uiautomator", "dump", DUMP)
        xml = self.run("shell", "cat", DUMP)
        self.run("shell", "rm", "-f", DUMP)
        found = []
        for match in re.finditer(r"<node ([^>]*)>", xml):
            attrs = dict(re.findall(r'([\w-]+)="([^"]*)"', match.group(1)))
            box = [int(n) for n in re.findall(r"\d+", attrs.get("bounds", ""))]
            if len(box) == 4:
                attrs["box"] = tuple(box)
                found.append(attrs)
        return found

    def find(self, pattern):
        rx = re.compile(pattern)
        for node in self.nodes():
            if rx.search(node.get("text", "")) or rx.search(node.get("content-desc", "")):
                return node
        return None

    @staticmethod
    def centre(node):
        x1, y1, x2, y2 = node["box"]
        return str((x1 + x2) // 2), str((y1 + y2) // 2)


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("-s", "--serial", help="adb device serial (adb devices)")
    parser.add_argument("steps", nargs="+")
    args = parser.parse_args()
    sys.stdout.reconfigure(encoding="utf-8", line_buffering=True)
    device = Device(args.serial)
    shots = Path(os.environ.get("WYSTORE_SHOTS", ROOT / "build" / "adb-shots"))

    for step in args.steps:
        op, _, arg = step.partition(":")
        if op in ("tap", "long", "expect"):
            node = device.find(arg)
            if node is None:
                print(f"not found: {arg}")
                sys.exit(1)
            label = node.get("text") or node.get("content-desc")
            if op == "expect":
                print(f"found {label!r} {node['box']}")
                continue
            x, y = device.centre(node)
            if op == "tap":
                device.run("shell", "input", "tap", x, y)
            else:
                device.run("shell", "input", "swipe", x, y, x, y, "800")
            print(f"{op} {label!r} {node['box']}")
        elif op == "texts":
            for node in device.nodes():
                label = node.get("text") or node.get("content-desc")
                if label:
                    print(node["box"], label)
        elif op == "type":
            device.run("shell", "input", "text", arg.replace(" ", "%s"))
        elif op == "top":
            for _ in range(int(arg or 4)):
                device.run("shell", "input", "swipe", "600", "700", "600", "2300", "300")
        elif op == "down":
            distance = int(arg or 1200)
            device.run("shell", "input", "swipe", "600", "2000", "600", str(max(200, 2000 - distance)), "400")
        elif op == "back":
            device.run("shell", "input", "keyevent", "4")
        elif op == "launch":
            device.run("shell", "monkey", "-p", PACKAGE, "-c", "android.intent.category.LAUNCHER", "1")
        elif op == "sleep":
            time.sleep(float(arg))
        elif op == "shot":
            shots.mkdir(parents=True, exist_ok=True)
            path = shots / f"{arg or 'screen'}.png"
            # exec-out, not a shell redirect: PowerShell's ">" re-encodes the bytes and the PNG
            # comes out unreadable.
            path.write_bytes(device.run("exec-out", "screencap", "-p", binary=True))
            print(path)
        else:
            sys.exit(f"unknown step: {step}")


if __name__ == "__main__":
    main()
