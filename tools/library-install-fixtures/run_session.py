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
    args = parser.parse_args()
    adb = str(Path(os.environ["ANDROID_HOME"]) / "platform-tools" / "adb.exe")
    prefix = [adb, "-s", args.serial]
    args.log.parent.mkdir(parents=True, exist_ok=True)
    with args.log.open("w", encoding="utf-8") as output:
        process = subprocess.Popen(prefix + ["shell", "am", "instrument", "-w", "-e", "class",
            "dev.wystore.fixture.LibraryInstallProbe#installCompleteSetThroughPackageInstaller", "-e", "version", str(args.version),
            "app.wystore.test/androidx.test.runner.AndroidJUnitRunner"], stdout=output, stderr=subprocess.STDOUT,
            creationflags=subprocess.CREATE_NO_WINDOW)
        deadline = time.monotonic() + 55
        while process.poll() is None and time.monotonic() < deadline:
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
