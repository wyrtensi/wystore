# Wy Store Library Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the curated Wy Store Library as an independent third source with shared cards, selectable update sources and complete compatible APK installation.

**Architecture:** A dedicated library client reads discovery, localized metadata and the canonical version feed. Pure policies select variants and decide source changes; existing persistence, workers, queue and installer carry the resulting requests. Integrate the existing ownership fix before changing its shared interfaces or adding database migrations.

**Tech Stack:** Existing Kotlin, Compose, Gson, OkHttp, Room, WorkManager and JUnit/MockWebServer; existing Python unittest tooling in the library repository. No new runtime dependency is required.

**Spec:** `docs/superpowers/specs/2026-11-01-library-source-design.md`, approved on 2026-11-01.

## Global Constraints

- No release, tag or publication of an Android build. No push without a separate request.
- Stop all workers/subagents and save a handoff before five-hour allowance reaches 15% remaining; check actual account usage between tasks. Begin closing out at 18% remaining. Never spend reset credits automatically.
- Preserve and integrate the other session's ownership fix; the original checkout remains read-only to this implementation.
- Do not add Google account authorization to the Android application.
- Keep Wy Store's current minimum Android version (API 28).
- Keep existing generic GitHub release selection, custom repositories and update behaviour.
- Never download APKs merely to check whether a newer version exists; equal/older unattended candidates fetch zero APK bytes.
- Install every member of one independently verified compatible variant. Do not mix variants, repackage APKs, re-sign them or fall back to base-only installation.
- Persist the chosen source; do not automatically fall back, downgrade, replace signatures or erase ownership/Play-pause state.
- Editorial `foreign_category` defaults to `true`; `false` removes only category membership.
- Speak Russian to the operator; code/comments/commits are English. New UI text must exist in all eight shipped languages.
- Verify local Git identity and GitHub login separately as `wyrtensi <wyrtensi@gmail.com>` / `wyrtensi` before commits. No assistant attribution.
- No skipped tests, swallowed errors, weakened checks or hardcoded fixture exceptions. Report blockers.

## Review Focus

- A source switch during a ready install or a late installer callback must not reinstall/register the abandoned source: Task 5 persisted revision tests and Task 7 process-death integration.
- Metadata refresh failure must not undo an editorial exclusion: Task 1 explicit cached-card failure regression.
- A newer default ARM64 variant must not mask a usable older cohort or force an unsupported install: Task 3 mixed-cohort fixture.
- A changed current signer can have valid rotation lineage: Task 5 metadata-unknown/manual-verification regression, without premature removal.
- A stale feed can reference deleted assets while another store installs a newer version: Task 4 one-refresh HTTP regression with fresh installed state and zero subsequent APK requests.

## Repositories, preparation and stop record

Android work stays on `library-source-design`; the clean baseline is `e507615`
plus the design documentation commit. A baseline verification already produced
`788 run, 0 failed, 0 errors, 0 skipped` / `VERIFY OK`. This does not verify the
future feature or ownership snapshot.

The other session's uncommitted ownership fix is captured privately under
`outputs/ownership-integration/`: `ownership.patch`, `files/`, and `manifest.json`.
The snapshot contains 32 tracked file changes and the exported Room schema 5,
with per-file hashes, base revision and patch digest. It includes unrelated
ownership code but no signing credentials. These ignored files must not be
published. The live source may have advanced; recheck before consuming it.

The library repository is the existing `wystore-library` checkout. Inspect its
status and session mailbox, then use an isolated task branch for Task 1 if
needed. Its work must not trigger downloads/publication merely to change cards.

On stop, record both repository HEADs/branches, completed task checkboxes,
commands/results, uncommitted paths, snapshot location and the next exact task
in `outputs/library-integration/handoff.md`. Terminate owned child processes and
agents, stop only disposable emulators started here, and leave user tabs/devices
alone. Never call an unfinished feature complete because the budget ran out.

## File structure and interface ownership

New Android units under `app/src/main/java/dev/wystore/`:

| File | Responsibility |
| --- | --- |
| `data/library/LibraryModels.kt` | Typed discovery, metadata, version, compatibility and publication records. |
| `data/library/LibraryJson.kt` | Strict parsing and library-specific reference/path validation. |
| `data/library/LibraryRepository.kt` | Shared HTTP reads, disk snapshot/cache, conditional refresh and metadata loading. |
| `data/library/LibraryVariantPolicy.kt` | Pure device compatibility and deterministic complete-set selection. |
| `data/library/LibraryDeviceSpec.kt` | Device facts supplied to the pure policy. |
| `data/library/LibraryUpdatePolicy.kt` | Native version decision and immutable resolved request. |
| `data/AppSourceIdentity.kt` | Source-specific identity, package merge key and source choices. |
| `data/AppSourceSwitchPolicy.kt` | Source-switch decisions independently of update ownership. |
| `updates/SourceSelectionCoordinator.kt` | Durable source changes and exclusion of obsolete queue/session actions. |

Modify existing source models, workers, queue/database/registration, backup and
settings codecs at their integration boundaries. Reuse `SecureArtifactDownloader`,
`SigningVerifier`, `SigningContinuity`, and `InstallSessionWriter`; do not fork them.
UI changes belong in existing search/details/category/settings/TV components,
with a small shared `ui/components/AppSourceSelector.kt` if required.

Library changes are limited to `wystore_library/metadata.py`,
`wystore_library/sync.py` configuration validation, their tests, `apps.json`
documentation and additive public discovery fields. Existing editorial settings
and APK publishing contracts remain intact.

### Task 0: Integrate the preserved ownership fix

**Files:** The 33 paths enumerated by `outputs/ownership-integration/manifest.json`; retain `app/schemas/dev.wystore.data.local.WyStoreDatabase/5.json`.

**Interfaces:**
- Consumes: snapshot at base `e507615`, or the owning session's verified commit if now available.
- Produces: baseline with separate `requiresUpdateOwnership` and `requiresUserConfirmation` throughout pending updates, queue, policy and prepared-install dispatch; Room `MIGRATION_4_5`; `AdoptionPolicy.canTrackInPlace(handover)`.

- [x] **Step 1:** Compare live HEAD and per-file hashes with the snapshot. Read all session mailboxes and coordinate any newer/claimed patch; never overwrite the live source. Prefer the owner's completed commit when available. If the snapshot is still authoritative, run `git apply --check outputs/ownership-integration/ownership.patch` in this isolated branch, then apply it and copy the exported schema 5 from the verified snapshot.
- [x] **Step 2:** Run `python tools/verify.py --tests "*AdoptionPolicyTest"`, then the ownership install-policy test classes and `python tools/verify.py`. Expected: `VERIFY OK`, no failed/errors/skips; preserve tests for Android without ownership support, explicit confirmation with auto-install disabled, queue retention and prepared-confirmation dispatch.
- [x] **Step 3:** Inspect the staged patch against the snapshot, including both confirmation flags and migration registration. Commit only the imported fix paths as the repository owner, or retain the original owner's commit. Do not cherry-pick the same changes twice.

### Task 1: Publish optional foreign-category membership

**Files:** Library repository `wystore_library/metadata.py`, `wystore_library/sync.py`, `tests/test_metadata.py`, `tests/test_sync.py`, `docs/operations.md`.

**Interfaces:**
- Consumes: `apps.json` entries with optional `foreign_category: bool`.
- Produces: discovery cards with resolved `foreign_category`, plus additive `locales` search summaries keyed by `ru_RU` / `en_US` (title and short description). Existing top-level card keys remain available.

- [x] **Step 1:** Add failing tests asserting missing/true -> included, false -> excluded, nonboolean -> explicit configuration error. Extend the metadata-failure fixture: cached artwork remains, but the current flag overrides the cached card and the refresh still reports the upstream failure. Assert every current configured app defaults to included without a hardcoded count.
- [x] **Step 2:** Run `.venv/Scripts/python.exe -m unittest tests.test_metadata tests.test_sync`; capture the meaningful failing assertions before implementing.
- [x] **Step 3:** Validate the optional setting before network work; carry resolved editorial membership into both freshly built and retained cached cards. Publish localized search title/summary from metadata already fetched in this job; never require new per-search network calls. Document one exclusion example without altering existing thematic categories or release retention.
- [x] **Step 4:** Run `.venv/Scripts/python.exe -m unittest discover -s tests` and `git diff --check`. Expected: all library tests pass, no live APK transfer. Commit explicit paths on the library task branch; no push/workflow dispatch.

### Task 2: Parse and cache the library contract

**Files:** Create `data/library/LibraryModels.kt`, `LibraryJson.kt`, `LibraryRepository.kt`; add matching JVM tests and sanitized fixtures under `app/src/test/resources/library/`.

**Interfaces:**
- `LibraryJson.parseCatalog(json: String): LibraryCatalog`
- `LibraryJson.parseIndex(json: String): LibraryIndex`
- `LibraryJson.parseMetadata(json: String, packageName: String): LibraryMetadata`
- `LibraryRepository.catalog(forceRefresh: Boolean = false): LibraryRead<LibraryCatalog>` (suspending)
- `LibraryRepository.index(forceRefresh: Boolean = false): LibraryRead<LibraryIndex>` (suspending)
- `LibraryRepository.details(packageName: String, locale: String): LibraryRead<LibraryMetadata>` (suspending)
- `LibraryRead<T>` exposes value, stale status and a typed refresh error. A required fresh update check does not treat stale fallback as a successful remote check.
- `LibraryIndex` owns canonical applications. Each `LibraryApplication` owns variants and artifacts by ID/hash plus immutable publication hash. Each `LibraryVariant` has native version, signer set/scope, compatibility and exact member references.

- [x] **Step 1:** Add parser fixture assertions for all canonical fields, `schema == 1`, `variant_schema == 1`, duplicate package/variant/hash rejection, missing artifact reference, base count, wrong types, nonpositive sizes and invalid SHA-256. Add catalogue default/exclusion, RU/EN fallback and version-labelled changelog tests. A legacy `entries` default must never replace canonical variant selection.
- [x] **Step 2:** Add MockWebServer cases for one shared index request, ETag/304 reuse, no cache on first offline failure, stale catalogue with visible refresh error, and invalid feed preserving the last valid snapshot. Test traversal/arbitrary origins on artwork/metadata/release references and strict package/tag binding. Run focused verification and observe RED.
- [x] **Step 3:** Implement typed parsing and atomic disk snapshots, using the existing Gson/OkHttp stack and public library root. Relative URLs must stay under that root; asset origins/redirect handling must use existing permitted GitHub routes. Do not turn validation errors into empty catalogues. Coalesce concurrent reads; `forceRefresh` revalidates the network.
- [x] **Step 4:** Run `python tools/verify.py --tests "*LibraryJsonTest"`, `--tests "*LibraryRepositoryTest"` and full verification before committing these files. Expected: `VERIFY OK` for each, with all HTTP tests local.

### Task 3: Select a complete compatible variant

**Files:** Create `LibraryDeviceSpec.kt`, `LibraryVariantPolicy.kt`, `LibraryUpdatePolicy.kt`; tests `LibraryVariantPolicyTest.kt` and `LibraryUpdatePolicyTest.kt` in matching test packages.

**Interfaces:**
- `LibraryDeviceSpec(sdk: Int, abis: List<String>, density: Int, locales: List<String>, features: Set<String>, glEsVersion: Int)`.
- `LibraryVariantPolicy.select(app: LibraryApplication, device: LibraryDeviceSpec): LibrarySelection`.
- `LibrarySelection.Compatible` contains `ResolvedLibraryVersion`; `Unsupported` contains a stable reason enum.
- `ResolvedLibraryVersion` carries package, variant ID, native version/name, publication hash, signer metadata/scope and the ordered complete member/artifact records. It is the input to queue serialization and transfer.
- `LibraryUpdatePolicy.isNewer(installedVersionCode: Long, available: ResolvedLibraryVersion): Boolean` uses native versionCode only.

- [x] **Step 1:** Write representative failing assertions:

```kotlin
assertFalse(LibraryUpdatePolicy.isNewer(100, candidate(versionCode = 100)))
assertFalse(LibraryUpdatePolicy.isNewer(101, candidate(versionCode = 100)))
assertTrue(LibraryUpdatePolicy.isNewer(99, candidate(versionCode = 100)))
```

Also pin mixed-cohort selection: reject a newer ARM64/API35 default on an ARMv7/API28 device while selecting its valid older cohort; do not confuse sampled SDK with actual minSDK. Cover missing native libraries, feature/GL mismatch, required/provided splits and malformed sets. No x86-to-ARM inference from emulator translation.
- [x] **Step 2:** Test ranking: highest compatible native version, ABI order, locale, density suitability and stable ID. A 420-dpi resource cohort is not rejected solely because the device is 480 dpi. English fallback is explicit; all files come from one selected variant. SDK-scoped certificate metadata outside its scope is unconfirmed for preliminary comparison, not proof of a mismatch. Run focused tests to confirm RED.
- [x] **Step 3:** Implement the pure policies and an Android adapter that reads SDK/ABIs/features/GL/density/system locale. Preserve minimum client API 28. No network or UI in the selector.
- [x] **Step 4:** Run focused and full `python tools/verify.py`; commit policy/model/tests. A passing selector test is not a claim of runtime app compatibility.

### Task 4: Add the managed library source and update/transfer path

**Files:** Modify `data/Models.kt`, `StoreRepository.kt`, `background/UpdateCheckWorker.kt`, `UpdateDownloadWorker.kt`, `updates/MetadataCheckedDownload.kt`, `QueueRepository.kt`, `InstalledAppRegistrar.kt`, queue models/entities/DAO/database, backup models/codecs, `settings/AppSettings.kt`, `SettingsRepository.kt`, `SettingsCodec.kt`, and exhaustive source-label/diagnostic mappings. Add `AppSourceIdentity.kt` and a queue migration test. Export the next Room schema after 5.

**Interfaces:**
- Add `ManagedSource.WY_STORE`; keep `InstallSource.WY_STORE` (observed installer) semantically separate.
- `StoreApp` gains source identity with RuStore as the legacy default. Make its numeric `appId` optional for non-RuStore details; RuStore artifact resolution requires a real RuStore identity/ID. Do not manufacture hashed/numeric library IDs.
- `ManagedApp` gains `sourceRevision: Long = 0`; the selected source/repository remain the managed preference. Add `libraryEnabled: Boolean = true` to both settings models and their mappings, persistence, diagnostics and backup.
- Add `AvailableUpdateVersion.Library(versionCode: Long)` to the existing metadata gate.
- Persist a `LibraryInstallRequest` containing the complete `ResolvedLibraryVersion` and `sourceRevision` on library queue rows. JSON decoding must validate its contract. Existing rows default to no library request and revision 0.
- Preserve snapshot-added `requiresUserConfirmation` and `requiresUpdateOwnership`; add `MIGRATION_5_6` only if 5 is still the actual latest schema when implementation begins. Otherwise chain from the actual latest version and update both migration directions/tests without destructive fallback.

- [ ] **Step 1:** Add failing worker HTTP-boundary tests: a batch of several managed library apps fetches one revalidated index, equal/older/native-unknown candidates request zero APKs, and a newer variant requests exactly its member set. A Play update observed before transfer suppresses unattended work under the existing pause rules. Manual checks are not manual reinstall requests.
- [ ] **Step 2:** Add one-time 404 recovery cases: unchanged publication -> visible failure; changed publication -> discard obsolete partials and reselect; another installer now has the equal/newer version -> no retry APK request; a second 404 never loops. Verify corrupt hash, missing split, wrong package/version and incompatible signer block install. Run these tests to meaningful RED.
- [ ] **Step 3:** Wire library checks/transfers through existing queue/version/downloader/verifier/installer, maintaining fresh installed reads before bytes and install. Verify every member and complete set against the immutable request. After network waits reread selected-source revision. Keep generic GitHub tag/version semantics and RuStore checks unchanged. Handle new enum branches explicitly, with working behavior rather than placeholder success/error swallowing.
- [ ] **Step 4:** Add migration/backup/settings round-trip tests, including old RuStore/GitHub rows and unknown source values. Unknown persisted source values must report unsupported data rather than becoming RuStore. Ensure artifact registration records WY_STORE and does not clear sticky Play/ownership facts.
- [ ] **Step 5:** Run full `python tools/verify.py`, migration instrumentation on a disposable emulator when available, and inspect generated Room schema diff. Commit exact core paths. Before any emulator installation, run `python tools/verify.py --release` and follow `wystore-emulator`; reuse that verified build for subsequent checks until code changes.

### Task 5: Make source switching durable and race-safe

**Files:** Create `data/AppSourceSwitchPolicy.kt`, `updates/SourceSelectionCoordinator.kt`; modify `StoreRepository.kt`, adoption/takeover integration, queue/session dispatch and registration boundaries. Add `AppSourceSwitchPolicyTest.kt`, coordinator and stale-registration integration tests.

**Interfaces:**
- `AppSourceChoice` is a sealed source reference: RuStore(package), Library(package), or GitHub(repository, knownPackage). Package is required before using the choice to manage an installed app.
- `AppSourceSwitchPolicy.decide(installed: InstalledApp, candidate: AppSourceChoice, signature: SignatureCompatibility, committedInstall: Boolean): SourceSwitchDecision`.
- `SourceSwitchDecision` has `SaveChoice`, `OfferReplacement`, `DeferUntilInstallSettles`, or an explicit invalid/unavailable result. Unknown compatibility can save the user's choice but cannot authorize replacement.
- `SourceSelectionCoordinator.select(packageName: String, choice: AppSourceChoice): SourceSwitchResult` is suspending; `isCurrent(packageName: String, source: ManagedSource, revision: Long): Boolean` fences queued/late operations.
- Persist a monotonic selection revision and use the same locking/transaction discipline as management writes. A staged replacement keeps the existing choice until successful replacement; a later mismatch after saving an unknown source never triggers automatic rollback.

- [ ] **Step 1:** Add failing policy tests for same signer preserving data/settings, equal/older version without download, known mismatch -> explicit replacement, unknown GitHub signature -> saved choice with actual APK-time check, valid rotation -> no mandatory deletion, unavailable source -> no fallback, different package -> no merge. RuStore/library details metadata must be used before claiming compatibility unknown.
- [ ] **Step 2:** Add integration tests switching with DOWNLOADING, READY_TO_INSTALL and AWAITING_USER_CONFIRMATION requests: obsolete workers cannot commit/register; unrelated rows survive. A committed INSTALLING session defers the switch until reconciliation. Simulate process death and a delayed success callback after a persisted source revision change.
- [ ] **Step 3:** Implement coordinator plus narrow callbacks into existing cancellation/session handling. Reuse explicit adoption replacement and its verified staging/data-loss confirmation. Preserve the ownership fix's two confirmation flags and Android-version behaviour. A source selector is not an ownership reset.
- [ ] **Step 4:** Run focused policy/coordinator tests and full `python tools/verify.py`. Confirm refused/canceled replacement leaves the correct saved choice and installed data. Commit only this source-switch unit.

### Task 6: Expose search, category, badges and source selection

**Files:** Modify `StoreViewModel.kt`, `data/SearchSources.kt`, `data/CuratedCategories.kt`, category/home view models/repositories, existing search/details/library components and phone/TV navigation. Add a small shared `AppSourceSelector.kt` if needed. Modify source settings, source labels and all eight `values*/strings.xml`. Extend related policy and Compose tests.

**Interfaces:**
- Add `SearchSources.WY_STORE` and library inclusion alongside current ALL/RUSTORE/GITHUB; update `within(githubEnabled, libraryEnabled)` and persisted setting choices.
- `AppSourceIdentity` represents source-specific identity without synthetic RuStore IDs; merge by known package only. Keep unknown-package custom GitHub results distinct and preserve their existing entry route.
- Shared details receive available `AppSourceChoice` values, preview choice, saved managed choice and a source-selection callback through `SourceSelectionCoordinator`.
- Category slug `wy-foreign`, Russian label `Зарубежные`: read included library cards in both existing category modes, without RuStore requests. It is a category tile, not a new destination/tab.

- [ ] **Step 1:** Add failing search/category tests: package duplicate -> one card with both badges; unknown GitHub package -> separate result; exclusions disappear only from foreign category, not search or updates; GitHub disabled does not hide library; source-specific network failure preserves other results. Test cancellation of stale search and source-preview results.
- [ ] **Step 2:** Wire cached library discovery into existing search and category/home paths. Details load metadata only on demand. Installed/managed source takes priority; new install follows the entry context. Previewing a source never changes management; deliberate selection invokes Task 5. Keep generic GitHub details/releases UI intact when chosen.
- [ ] **Step 3:** Add source badges/selector and the ordinary category tile using existing UI components. Unsupported devices retain the card with an installability reason. Version/size describe the chosen variant; descriptions/screenshots/changelog come from localized metadata, without presenting a listing changelog as proof about an older delivered version. Missing ratings/reviews stay absent.
- [ ] **Step 4:** Use `tools/add_string.py` for new text in en/ru/be/uk/kk/lv/uz/zh, including source setting, foreign category, source selection, unconfirmed signature, unsupported variant and update errors. Add library/upstream links to source info. No user-facing model/backend jargon.
- [ ] **Step 5:** Test phone compact-width badge wrapping/selector interaction and TV focus/selection/back navigation. Run `python tools/verify.py`, required lint, and focused Compose instrumentation on disposable emulators. Commit exact UI/resources/tests paths after inspecting screenshots.

### Task 7: Final integration, verification and handoff

**Files:** Existing verification/emulator tooling; add realistic fixture tests under `app/src/androidTest` and `app/src/test` only when not already covered by earlier tasks. Update user-facing changelog for the eventual release without version bump/tag. Keep screenshots/logs/private manifests ignored under `outputs/library-integration/`.

**Interfaces:** All completed tasks, ownership baseline, both repository commits and current public library contract.

- [ ] **Step 1:** Check allowance before starting the final matrix. Run full library Python tests, `python tools/verify.py --release`, and `gradlew.bat :app:lintDebug :app:assembleDebugAndroidTest --console=plain`. Require fresh counts, correct release certificate and zero failures; report any unavailable environment rather than skipping a required check.
- [ ] **Step 2:** Following `wystore-emulator`, use disposable compatible phone/older-Android/TV emulators for new install, in-place update, source switch, signer-changing replacement decline/success, process-death resume, Play pause, alternate density and complete split sets. Preserve the user's phone and Google-login emulator. Include legacy ownership regression scenarios from Task 0.
- [ ] **Step 3:** Install library ChatGPT and Grok sets only when the emulator supports their actual compatibility requirements. Record installation, launch and sign-in separately. Do not ask for credentials in logs; if interactive account access requires the user, record that boundary accurately. Emulator translation failure is not a real ARM-device result.
- [ ] **Step 4:** Request one fresh Sol whole-branch review of the final Android changes and library contract change, including the imported ownership fix and Review Focus cases. Resolve material findings with targeted tests and rerun affected checks; do not add speculative review passes. No agent/model branding in deliverables.
- [ ] **Step 5:** Run `git diff --check` in both repositories, inspect status/identities and commit remaining exact paths. Update task checkboxes and the private handoff with results, emulator limits and both HEADs. Leave everything local: no push, tag, workflow dispatch or release.

## Execution checkpoint

Stopped for the operator's usage limit on 2026-11-01. Tasks 0-3 are complete;
Tasks 4-5 have compiled and tested core code, including migration/coordinator
instrumentation, but the full worker/install race matrix remains incomplete.
Task 6 is a local UI checkpoint, not a finished or reviewed feature. Task 7 is
partial: 833 JVM tests, release certificate validation, lint/debug/test builds
and four Android 14 instrumentation tests passed. TV, actual split-set/root
transactions, ChatGPT/Grok sign-in and final whole-branch review remain pending.
See `outputs/library-integration/handoff.md` before continuing. No push or release.

## Self-review record

- All approved spec areas map to Tasks 0-7: editorial contract, parsing/cache,
  variant choice, update/download integration, source changes, UI and final proof.
- All five Review Focus conditions have named tests in their owning tasks.
- Interfaces use native library versionCode, immutable complete sets and one
  source-revision concept; existing GitHub version/tag interpretation is preserved.
- Database work explicitly follows the ownership schema 5 and never competes
  with it. Both confirmation flags survive queue serialization and dispatch.
- The operator approved native execution. Resume from the execution checkpoint;
  do not repeat the imported ownership fix or completed catalogue/parser/policy work.
