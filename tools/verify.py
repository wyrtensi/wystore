#!/usr/bin/env python3
"""Run the checks behind "it works": unit tests, and optionally a signed release build.

    python tools/verify.py                 # unit tests only
    python tools/verify.py --release       # tests, assembleRelease, signing certificate check
    python tools/verify.py --tests "*AdoptionPolicyTest"

The result is counted from the JUnit XML files, and the old ones are deleted first: Gradle leaves
results of earlier runs in place, so a count over the whole directory after a run that did not
compile reports the previous run's success.

The release APK must be signed with the Wy Store certificate, or it cannot be installed over the
published app (and over the user's data). Exit code 0 only when everything asked for passed.
"""
import argparse
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RESULTS = ROOT / "app" / "build" / "test-results" / "testDebugUnitTest"
APK = ROOT / "app" / "build" / "outputs" / "apk" / "release" / "app-release.apk"
# Public: it is in every published release. A build signed with anything else cannot update one.
CERT_SHA256 = "33dcb50061a08190f68bbee2d743be38ad4f7307d98dc18dd7e76719c815a258"


def gradle(*tasks) -> int:
    wrapper = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
    cmd = [str(wrapper), *tasks, "-q"]
    proc = subprocess.run(cmd, cwd=ROOT, capture_output=True, text=True, encoding="utf-8", errors="replace")
    noise = ("SDK processing", "SDK XML file of version")
    for line in (proc.stdout + proc.stderr).splitlines():
        if line.strip() and not any(n in line for n in noise):
            print(line)
    return proc.returncode


def count_results():
    total = failures = errors = skipped = 0
    failed = []
    for xml in RESULTS.glob("*.xml"):
        text = xml.read_text(encoding="utf-8")
        head = re.search(r'<testsuite name="([^"]+)" tests="(\d+)" skipped="(\d+)" failures="(\d+)" errors="(\d+)"', text)
        if not head:
            continue
        total += int(head[2]); skipped += int(head[3]); failures += int(head[4]); errors += int(head[5])
        for case in re.finditer(r'<testcase name="([^"]+)" classname="([^"]+)"[^>]*>\s*<failure', text):
            failed.append(f"{case[2]} > {case[1]}")
    return total, skipped, failures, errors, failed


def sdk_dir() -> Path | None:
    for env in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        if os.environ.get(env):
            return Path(os.environ[env])
    props = ROOT / "local.properties"
    if props.exists():
        for line in props.read_text(encoding="utf-8").splitlines():
            if line.startswith("sdk.dir="):
                return Path(line.split("=", 1)[1].replace("\\:", ":").replace("\\\\", "\\"))
    return None


def apksigner() -> Path | None:
    sdk = sdk_dir()
    if not sdk:
        return None
    tools = sorted((sdk / "build-tools").glob("*"), key=lambda p: [int(x) if x.isdigit() else 0 for x in re.split(r"[.-]", p.name)])
    for folder in reversed(tools):
        for name in ("apksigner.bat", "apksigner"):
            if (folder / name).exists():
                return folder / name
    return None


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--release", action="store_true", help="also build and check the signed release APK")
    parser.add_argument("--tests", help="Gradle --tests filter, e.g. \"*QueueRowForTest\"")
    args = parser.parse_args()
    sys.stdout.reconfigure(encoding="utf-8", line_buffering=True)

    shutil.rmtree(RESULTS, ignore_errors=True)
    tasks = [":app:testDebugUnitTest"] + (["--tests", args.tests] if args.tests else [])
    code = gradle(*tasks)
    total, skipped, failures, errors, failed = count_results()
    print(f"unit tests: {total} run, {failures} failed, {errors} errors, {skipped} skipped (gradle exit {code})")
    for name in failed:
        print(f"  FAILED {name}")
    ok = code == 0 and total > 0 and failures == 0 and errors == 0

    if args.release and ok:
        code = gradle(":app:assembleRelease")
        if code != 0 or not APK.exists():
            print(f"release build failed (gradle exit {code})")
            ok = False
        else:
            signer = apksigner()
            if signer is None:
                print("apksigner not found; signature NOT checked")
                ok = False
            else:
                out = subprocess.run([str(signer), "verify", "--print-certs", str(APK)],
                                     capture_output=True, text=True).stdout
                digest = re.search(r"certificate SHA-256 digest: ([0-9a-f]{64})", out)
                if digest and digest[1] == CERT_SHA256:
                    print(f"release: {APK} ({APK.stat().st_size // 1024} KB), signed with the Wy Store certificate")
                else:
                    print(f"release: WRONG or missing signature ({digest[1] if digest else 'none'}); "
                          "it cannot be installed over the published app. Is keystore.properties present?")
                    ok = False

    print("VERIFY OK" if ok else "VERIFY FAILED")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
