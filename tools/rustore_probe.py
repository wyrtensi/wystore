#!/usr/bin/env python3
"""Ask RuStore about packages the way Wy Store does, to tell "not there" from "no answer".

    python tools/rustore_probe.py com.yandex.yamb ru.yandex.music

Without the headers the app sends, the API answers 400 for every package, which looks exactly
like the package being gone. With them a 404 carries RuStore's own reason - for example
"Couldn't find required data, error: android_app_ver": the page exists, but no version is
offered for installation. The app treats any 4xx as "not in RuStore" (RuStoreRateLimitPolicy).
"""
import json
import sys
import urllib.error
import urllib.request

API = "https://backapi.rustore.ru/applicationData/overallInfo/{}"
PAGE = "https://www.rustore.ru/catalog/app/{}"
# RuStoreApiCompatibilityPolicy.DEFAULT_VERSION_CODE; the app may have learned a newer one.
VERSION_CODE = "110910"


def get(url: str, api: bool):
    headers = {"User-Agent": "WyStore/probe"}
    if api:
        headers.update({"Accept": "application/json", "ruStoreVerCode": VERSION_CODE})
    request = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            return response.status, response.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as error:
        return error.code, error.read().decode("utf-8", "replace")


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    for package in sys.argv[1:]:
        status, body = get(API.format(package), api=True)
        page_status, _ = get(PAGE.format(package), api=False)
        try:
            data = json.loads(body)
        except ValueError:
            data = {}
        app = data.get("body") or {}
        if status == 200 and app:
            print(f"{package}: OK  {app.get('appName')}  version {app.get('versionName')} ({app.get('versionCode')})")
        else:
            print(f"{package}: API {status} {data.get('message') or body[:120]!r}; catalogue page {page_status}")


if __name__ == "__main__":
    main()
