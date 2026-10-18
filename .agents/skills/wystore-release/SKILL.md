---
name: wystore-release
description: Cut a Wy Store release - changelog section, version bump, tag, and checking what CI published. Use when asked to release, publish a version, tag, or ship. Never release without being asked; finishing a feature is not a request to release.
---

# Releasing Wy Store

Releases are tag-driven. Pushing `vX.Y.Z` runs `.github/workflows/release.yml`: unit tests,
signing with the keystore from secrets, `apksigner` check, and a GitHub release whose body is the
`CHANGELOG.md` section for that version. A tag without its section fails the workflow on purpose
(`.github/workflows/changelog-section.sh`).

Only when the user asked for a release, and only after the change was checked on a device when it
is visible (`wystore-verify`, `wystore-device`).

## Steps

1. **Changelog first.** Add `## [X.Y.Z] — YYYY-MM-DD` at the top of `CHANGELOG.md` (Russian, Keep
   a Changelog, newest first):
   - an opening paragraph saying what this version is about for the user;
   - `### Добавлено` / `### Исправлено` / `### Изменено` with bold leads: what changed for the user
     and what was wrong before - the voice of the commit messages, not a list of commit subjects;
   - the closing "Файлы релиза" note, in every version section (the release body is built from the
     section alone): the two APKs, `wystore-X.Y.Z.apk` and `wystore.apk`, are the same build with the
     same signature, either one will do. Copy the wording from the previous section.
2. **Bump** `versionCode` (+1) and `versionName` in `app/build.gradle.kts`, and the site's
   `"softwareVersion"` in the JSON-LD of all eight pages (`docs/index.html`, `docs/*/index.html`)
   to the same `X.Y.Z` - its `downloadUrl` points at the latest release, and nothing else updates
   it: it sat at 0.2.1 until 0.4.1. Set `<lastmod>` in `docs/sitemap.xml` to the release date.
   Pushing `docs/` deploys the site (`.github/workflows/site.yml`).
3. `python tools/verify.py --release` - must end with `VERIFY OK`.
4. Commit (`Version X.Y.Z, with its changelog`), then tag `vX.Y.Z` on that commit.
5. Push the branch and the tag only when the user said to publish.
6. Watch the Release workflow (`gh run list --workflow release.yml`, `gh run watch`), then check the
   release page: both APKs present, body is the changelog section.
7. Close issues the release fixed that did not close from `Fixes #N` in commit messages, with a
   short comment saying which version has the fix.

## Rules

- Git identity is the repository owner's; no assistant attribution in commits, tags or release
  text.
- Never move or delete a published tag. A broken release is fixed by the next version.
- The signing keystore and `keystore.properties` never leave the machine and are never committed.
- After publishing, the user's phone can be updated to the release with `adb install -r` of the
  published APK, which keeps its data.
