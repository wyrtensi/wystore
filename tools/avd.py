#!/usr/bin/env python3
"""Start and stop Android emulators for Wy Store scenarios that must not run on a real phone.

    python tools/avd.py list
    python tools/avd.py start wy_api34              # with a window
    python tools/avd.py start wy_api34 --headless   # no window, for scripted checks
    python tools/avd.py start wy_api34 --wipe       # factory-fresh data first
    python tools/avd.py stop                        # every running emulator

"start" returns once Android has finished booting and prints the serial (emulator-5554, ...)
to pass to adb -s and tools/adb_ui.py -s. Nothing an emulator run does is saved back into its
snapshot, so the next start begins from the same state.
"""
import argparse
import os
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
from adb_ui import find_adb  # noqa: E402


def emulator_exe() -> str:
    adb = Path(find_adb())
    exe = "emulator.exe" if os.name == "nt" else "emulator"
    candidate = adb.parent.parent / "emulator" / exe
    if not candidate.exists():
        sys.exit(f"emulator not found at {candidate}; install it with the SDK manager")
    return str(candidate)


def adb(*args) -> str:
    out = subprocess.run([find_adb(), *args], capture_output=True, text=True)
    return (out.stdout + out.stderr).strip()


def running() -> list[str]:
    return [line.split()[0] for line in adb("devices").splitlines()[1:]
            if line.startswith("emulator-") and line.split()[-1] == "device"]


def start(name: str, headless: bool, wipe: bool, timeout: int) -> None:
    before = set(running())
    cmd = [emulator_exe(), "-avd", name, "-no-snapshot-save", "-no-boot-anim", "-netdelay", "none", "-netspeed", "full"]
    if headless:
        cmd += ["-no-window", "-no-audio"]
    if wipe:
        cmd.append("-wipe-data")
    flags = subprocess.CREATE_NEW_PROCESS_GROUP | subprocess.DETACHED_PROCESS if os.name == "nt" else 0
    subprocess.Popen(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, creationflags=flags,
                     start_new_session=os.name != "nt")
    deadline = time.time() + timeout
    serial = None
    while time.time() < deadline:
        new = [s for s in running() if s not in before]
        if new:
            serial = new[0]
            if adb("-s", serial, "shell", "getprop", "sys.boot_completed") == "1":
                # Unlock and keep the screen on: a scripted check reads a lock screen otherwise.
                adb("-s", serial, "shell", "input", "keyevent", "82")
                adb("-s", serial, "shell", "svc", "power", "stayon", "true")
                print(serial)
                return
        time.sleep(3)
    sys.exit(f"{name} did not finish booting in {timeout}s" + (f" (serial {serial})" if serial else ""))


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("list")
    s = sub.add_parser("start")
    s.add_argument("name")
    s.add_argument("--headless", action="store_true")
    s.add_argument("--wipe", action="store_true")
    s.add_argument("--timeout", type=int, default=300)
    sub.add_parser("stop")
    args = parser.parse_args()

    if args.command == "list":
        print(subprocess.run([emulator_exe(), "-list-avds"], capture_output=True, text=True).stdout.strip())
        if running():
            print("running:", " ".join(running()))
    elif args.command == "start":
        start(args.name, args.headless, args.wipe, args.timeout)
    elif args.command == "stop":
        for serial in running():
            adb("-s", serial, "emu", "kill")
            print("stopped", serial)


if __name__ == "__main__":
    main()
