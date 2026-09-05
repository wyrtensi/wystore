# RuStore API compatibility fix

**Goal:** Remove HTTP 419 failures without requiring a RuStore account or `User-Token`, while keeping the compatibility value discoverable from the signed official RuStore APK.

**Architecture:** Keep the official APK manifest identity separate from the undocumented `ruStoreVerCode` API header. Derive the first API candidate from the APK version name (`1.108.0.2` -> `110802`), verify it with a lightweight `overallInfo` probe, and persist only a candidate accepted by the backend. Every real RuStore API request retries only HTTP 417/419 through a bounded fallback ladder.

**Compatibility:** Android 8 remains supported (minSdk 26). The network layer uses existing OkHttp APIs and no API-level-specific Android calls. Android 13+ behavior and root/manual installation paths are untouched.

## Tasks

1. Add unit tests for version-name conversion, the default candidate, bounded retry order, fallback, and legacy backup deserialization.
2. Add a pure compatibility policy with default `110802`, fallback candidates `1000000` and `247`, and three attempts per candidate for HTTP 417/419.
3. Route `overallInfo` and `v2/download-link` through the shared fallback executor and persist the accepted API code.
4. Change the official APK compatibility worker to derive and probe an API code instead of copying the manifest versionCode into the header.
5. Separate API and verified APK values in the model/preferences/UI while retaining migration from the old preference and backup field.
6. Bump WyStore to 0.1.9, document the endpoints and monkeypatch rationale, run unit tests/lint/release build, sign with the existing key, and copy the APK to the connected phone's Downloads directory.

