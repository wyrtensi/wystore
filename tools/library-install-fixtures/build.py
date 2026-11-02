"""Build small, original, signed base/split APKs for disposable emulator install checks."""
import argparse
import json
import os
from pathlib import Path
import subprocess
from xml.sax.saxutils import quoteattr


def run(*args):
    subprocess.run([str(a) for a in args], check=True, stdout=subprocess.DEVNULL)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--sdk", type=Path, default=os.environ.get("ANDROID_HOME"))
    args = parser.parse_args()
    if not args.sdk:
        parser.error("Set ANDROID_HOME or pass --sdk")
    sdk = Path(args.sdk)
    tools = max((sdk / "build-tools").iterdir(), key=lambda p: tuple(int(v) for v in p.name.split(".") if v.isdigit()))
    android = max((sdk / "platforms").glob("android-*/android.jar"), key=lambda p: int(p.parent.name.split("-")[-1]))
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=True)
    package = "dev.wystore.installfixture"
    password = "installation-fixture"
    for signer in ("original", "different"):
        key = out / (signer + ".jks")
        if not key.exists():
            run("keytool", "-genkeypair", "-keystore", key, "-alias", "fixture", "-storepass", password,
                "-keypass", password, "-keyalg", "RSA", "-validity", "3650", "-dname", "CN=Installation Fixture")
    assets = out / "assets"
    assets.mkdir(exist_ok=True)
    for version, signer in ((1, "original"), (2, "original"), (2, "different")):
        for split in (None, "config.en"):
            name = f"{'base' if split is None else 'split'}-v{version}-{signer}"
            directory = out / name
            directory.mkdir(exist_ok=True)
            attributes = '' if split is None else f' split={quoteattr(split)} android:isFeatureSplit="false"'
            required = ' android:isSplitRequired="true"' if split is None else ''
            manifest = directory / "AndroidManifest.xml"
            manifest.write_text(f'''<manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="{package}" android:versionCode="{version}" android:versionName="{version}"{attributes}>
                <uses-sdk android:minSdkVersion="28" android:targetSdkVersion="34"/>
                <application android:hasCode="false" android:debuggable="true" android:label="Installation fixture"{required}/>
                </manifest>''', encoding="utf-8")
            unsigned = directory / "unsigned.apk"
            aligned = directory / "aligned.apk"
            run(tools / "aapt2.exe", "link", "-o", unsigned, "--manifest", manifest, "-I", android)
            run(tools / "zipalign.exe", "-f", "4", unsigned, aligned)
            apk = assets / (name + ".apk")
            run(tools / "apksigner.bat", "sign", "--ks", out / (signer + ".jks"), "--ks-key-alias", "fixture",
                "--ks-pass", f"pass:{password}", "--key-pass", f"pass:{password}", "--out", apk, aligned)
            run(tools / "apksigner.bat", "verify", "--verbose", apk)
    print(json.dumps({"package": package, "assets": str(assets), "files": sorted(p.name for p in assets.glob("*.apk"))}))


if __name__ == "__main__":
    main()
