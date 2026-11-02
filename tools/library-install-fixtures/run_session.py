"""Confirm only the disposable installation fixture's system dialog while its probe runs."""
import argparse
from pathlib import Path
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--version", type=int, required=True)
    parser.add_argument("--log", type=Path, required=True)
    parser.add_argument("--method", default="installCompleteSetThroughPackageInstaller", choices=[
        "installCompleteSetThroughPackageInstaller", "verifiedSignerReplacementCanBeDeclinedOrCompleted"])
    args = parser.parse_args()
    adb = str(Path(os.environ["ANDROID_HOME"]) / "platform-tools" / "adb.exe")
    prefix = [adb, "-s", args.serial]
    args.log.parent.mkdir(parents=True, exist_ok=True)
    with args.log.open("w", encoding="utf-8") as output:
        process = subprocess.Popen(prefix + ["shell", "am", "instrument", "-w", "-e", "class",
            "dev.wystore.fixture.LibraryInstallProbe#" + args.method, "-e", "version", str(args.version),
            "app.wystore.test/androidx.test.runner.AndroidJUnitRunner"], stdout=output, stderr=subprocess.STDOUT,
            creationflags=subprocess.CREATE_NO_WINDOW)
        deadline = time.monotonic() + 55
        verified_phases = set()
        while process.poll() is None and time.monotonic() < deadline:
            transcript = args.log.read_text(encoding="utf-8")
            for phase in ("marker-create", "marker-retained", "marker-removed"):
                if f"fixture_phase={phase}" not in transcript or phase in verified_phases:
                    continue
                fixture = "dev.wystore.installfixture"
                if phase == "marker-create":
                    subprocess.run(prefix + ["shell", "run-as", fixture, "mkdir", "-p", "files"], check=True)
                    subprocess.run(prefix + ["shell", f"run-as {fixture} sh -c 'printf retained > files/replacement-marker'"], check=True)
                if phase in {"marker-create", "marker-retained"}:
                    marker = subprocess.check_output(prefix + ["shell", "run-as", fixture, "cat", "files/replacement-marker"])
                    if marker.strip() != b"retained":
                        raise RuntimeError("The fixture's private data marker changed")
                else:
                    subprocess.run(prefix + ["shell", "run-as", fixture, "pwd"], check=True, stdout=subprocess.DEVNULL)
                    subprocess.run(prefix + ["shell", "run-as", fixture, "mkdir", "-p", "files"], check=True)
                    marker = subprocess.check_output(prefix + ["shell", "run-as", fixture, "find", "files", "-name", "replacement-marker"])
                    if marker.strip():
                        raise RuntimeError("The removed fixture's private data marker survived replacement")
                subprocess.run(prefix + ["shell", "run-as", "app.wystore", "touch", f"cache/library_probe_{phase}"], check=True)
                verified_phases.add(phase)
                print(f"Verified fixture phase: {phase}", flush=True)
            subprocess.run(prefix + ["shell", "uiautomator", "dump", "/sdcard/library_install_probe.xml"],
                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=10, check=False)
            dumped = subprocess.check_output(prefix + ["shell", "cat", "/sdcard/library_install_probe.xml"])
            document = ET.fromstring(dumped)
            texts = [node.get("text", "") for node in document.iter("node")]
            if "Installation fixture" in texts:
                for node in document.iter("node"):
                    if node.get("text", "").casefold() in {"install", "update"} and node.get("enabled") == "true":
                        coordinates = list(map(int, re.findall(r"\d+", node.get("bounds", ""))))
                        if len(coordinates) != 4:
                            raise RuntimeError("Installation button bounds are missing")
                        x1, y1, x2, y2 = coordinates
                        subprocess.run(prefix + ["shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2)], check=True)
                        print("Confirmed the installation fixture dialog", flush=True)
                        break
            time.sleep(.3)
        process.wait(timeout=10)
    result = args.log.read_text(encoding="utf-8")
    print(result)
    if not re.search(r"OK \(1 test\)", result):
        raise SystemExit("The runtime probe did not pass")


if __name__ == "__main__":
    main()
