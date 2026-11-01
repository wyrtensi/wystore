# Wy Store Library source integration

Date: 2026-11-01
Status: Approved by the operator on 2026-11-01 after the builder check.
Product implementation is in progress; the current checkpoint is not release-ready.
Do not release. Stop all execution before
the five-hour allowance falls to 15% remaining, unless the work finishes earlier.

## Intent

Expose the public `wyrtensi/wystore-library` catalogue as a third application
source named Wy Store, alongside RuStore and GitHub. Users must not supply Google
credentials. The library publishes selected original publisher-signed applications,
currently obtained from Google Play, through transparent GitHub workflows.
The primary purpose is access to applications unavailable in the Russian region.

This is an integration project across the existing Android client and a small
catalogue contract addition in the library repository. It does not add a direct
Google Play client or an account dispenser to the Android application.

## Decisions made by the operator

- Use an independent library parser, variant selection and update policy, while
  reusing existing application cards, download queue, verification and installation.
- Keep the existing GitHub source's release selection, custom repositories,
  installation and update behaviour.
- Show every known available source as a badge on an application's existing card.
  Offer source selection inside the application details page.
- For an installed application, compatible signatures allow future updates from
  the selected source. Incompatible signatures offer the existing explicit
  uninstall/reinstall flow and its data-loss warning.
- Add the ordinary category `Зарубежные` among the existing category tiles.
  Include all current library applications, and new library applications by
  default, while allowing selected entries to be excluded.
- Do not download APKs merely to check whether a newer version exists.
- RuStore details and the library version feed already provide certificate
  fingerprints. Use them before downloading. Generic GitHub releases retain
  their existing APK-time signature checks; no mandatory preflight APK download
  is added just to select a source.

## Approaches considered

1. Dedicated library source integrated into the existing cards, queue and
   installer. This needs explicit application/source identity and library-specific
   policies, but shares installation and avoids a second management interface.
   The operator selected this approach.
2. Separate library section with its own application pages and installation
   flow. Isolation is possible, but source selection and background updates still
   require shared integration, while details and management logic would diverge.

A generic GitHub import of `wystore-library` cannot represent this source:
the library has one release per package, complete split sets and several device
variants. The generic GitHub source intentionally keeps its existing behaviour.

## Scope and constraints

Reuse Kotlin, Compose, WorkManager, the existing persistence, HTTP clients,
downloader and PackageInstaller routes. Keep Wy Store's current minimum Android
version; publishing an older APK cohort does not lower the client's minimum SDK.
Cover phone and TV interaction with the same source decisions and compatibility
policy. Android profile bridges remain a separate project; the current installed
application/profile scope applies here.

Do not promise access to Google Play's entire catalogue, universal device
compatibility, availability in every region, successful sign-in, subscriptions,
Play Integrity or dynamic delivery. Do not add automatic source fallback,
automatic signature replacement, automatic downgrades, APK repacking or signing.
Do not release, tag or push the Android application as part of this design task.

Implementation must coordinate with ongoing ownership/install work and integrate
the then-current ownership logic; it must not overwrite unrelated changes.

## Existing implementation and required boundaries

`ManagedSource` and `SearchSources` currently enumerate RuStore and GitHub.
`StoreApp` carries a numeric `appId` and some RuStore-specific fields. Search and
details in `StoreViewModel`, source selection in workers, queue persistence,
registration, backup/import and diagnostics must recognise the third source.

Do not invent numeric RuStore IDs for library applications. Introduce an explicit
source reference with a source-specific identifier: package for the library,
the existing RuStore reference, and repository identity for GitHub. Android
package name is the merge key only when a source actually knows it. Preserve
custom GitHub entries whose package is unknown until APK verification.

The selected update source, observed installer/update owner, signing identity
and source availability are separate facts. A `Wy Store` catalogue badge does
not assert that Android assigned update ownership to this installation.

## Library and publication contract

Use the existing public endpoints under
`https://raw.githubusercontent.com/wyrtensi/wystore-library/main/library/`:

- `catalog.json`: lightweight discovery cards, used for local search and category
  membership. It already supplies package, title, developer, icon and metadata path.
- `index.json`: one version/variant feed per check, containing `schema: 1`,
  `variant_schema: 1`, `applications`, `delivery_profiles`, and legacy `entries`.
  Select from canonical `applications`; never select the default legacy entry
  without checking compatibility.
- `apps/<package>/metadata.json`: localized descriptions, screenshots, icons,
  display metadata and changelog history, fetched when details are opened.

Published APK versionCode and versionName come from the selected verified variant.
Store listing version strings are not the authority for updates or displayed
installable versions. Do not associate a listing changelog with an older delivered
build as if it definitely describes that build; retain any version label supplied
with those notes.

### Optional foreign-category membership

Proposed technical contract: accept optional boolean `foreign_category` in each
operator-maintained `apps.json` entry, defaulting to `true`. Publish its resolved
value in the matching discovery card in `catalog.json`. Missing fields in older
catalogues also default to `true`; invalid types fail validation.

An entry with `foreign_category: false` remains available in search, details,
source choices and update checks. Only its membership in `Зарубежные` changes.
This is an editorial setting in the repository. No end-user category settings
screen is added. Existing thematic `category` values in `apps.json` are preserved.
Membership is independent of successful metadata refresh: reuse cached artwork
on a metadata failure, but apply the current configured editorial flag.

This boolean and the operator-facing control location are design proposals,
not previously agreed implementation details.

## Discovery, categories and details

Add the `Wy Store` search filter beside the existing source filters. The new
source is enabled by default and has an independent source setting; turning off
GitHub does not turn off the library. These defaults are proposed here.

Search locally against the cached discovery catalogue by title, developer and
exact package. Add compact Russian/English search titles/summaries to discovery
cards if needed; do not fetch every details document during search. Refresh
discovery on first use, explicit refresh and normal freshness expiry. Existing
GitHub local search and RuStore network search keep their current triggers.
One failed source must not erase successful results from the others. Show a
source-specific load error rather than claiming that it has no applications.

Combine known matches by package into one card and append badges as availability
is discovered. Do not query every remote source for every card. The badges
describe known catalogue availability; unsupported or temporarily unreachable
sources must not trigger automatic switching.

Add `Зарубежные` to the existing category rail/grid and category browser with a
stable internal slug such as `wy-foreign`. It uses the library catalogue directly,
without mapping to a RuStore slug or creating a separate top-level destination.
Keep it available in either existing category-display mode when the library
source is enabled. RuStore failure must not prevent opening it. On first-load
failure show retry; with cached cards show the cached content and stale state.
Do not remove incompatible applications from this category; explain when a
particular device has no suitable published variant.

Use the existing application details layout for library metadata. When several
sources are known, offer a labelled source selector and distinguish the currently
managed source from a source being previewed. A new installation started from a
source/category uses that source unless the user explicitly changes it. For a
managed app, its saved source has priority. For an unmanaged merged search result,
preserve the existing result ordering rather than globally preferring the library.
Previewing metadata must not change the saved update source.

Show library/publication links and original upstream provenance in the source
information area. The library is hosted on GitHub; its user-facing source badge
is Wy Store. Reuse existing GitHub-specific details/actions when that source is
selected; do not force its releases into library semantics.

## Switching an installed application's source

| Situation | Required behaviour |
| --- | --- |
| Same package and compatible signing identity | Save the chosen update source without uninstalling or reinstalling; retain the app's data and auto-update preference. |
| New source has an equal or older version | Save the source when compatible; do not download or downgrade; wait for a newer compatible release. |
| Incompatibility established before source selection is committed | Offer the existing deliberate replacement flow, with app/data-loss warning; keep the old managed source if the user declines or replacement fails. |
| Signature compatibility is not yet known, particularly for GitHub | Save the user's chosen source, with compatibility unconfirmed. Verify the candidate during an actual install/update. Do not download an APK solely to complete source selection. |
| Incompatibility established during a later update from the saved source | Block installation and offer deliberate replacement or explicit source reselection. Declining replacement keeps the installed app and saved source; never automatically return to a previous source. |
| Different package under the same title | Keep separate applications; this is not an ordinary source switch. |
| Chosen source is unavailable or removed | Retain the preference and explain the problem; do not fall back automatically. |

Use existing signing continuity support for proven key rotation. A different
current certificate alone is insufficient to demand replacement. Metadata may
guide the decision, but the downloaded APK verification and Android remain the
final installability checks.

In the normal RuStore/library switch, first obtain RuStore details or the
selected library variant's certificate metadata. A search card without a
fingerprint is not a reason to introduce a new workflow: fetch its details.
Missing metadata remains unknown. Different current fingerprints require the
existing cautious treatment of possible key rotation rather than asserting
that removal is already necessary.

Do not describe a source switch as successful transfer of Android update
ownership. Preserve existing Google Play pause/notification settings and observed
ownership state; only verified ownership handover can clear its corresponding
flags. Offer the existing explicit handover action when needed, separately from
selecting the catalogue that supplies updates.

A signer-changing replacement must stage and verify a complete installable set
before offering removal. When a switch was held pending this replacement, commit
the new managed source only after successful installation. When the source was
already selected before a later incompatible update, a failed/canceled
replacement does not alter that preference. No automatic removal or downgrade
is permitted.

### Queue races and persistence

Source changes must fence workers, delayed callbacks and prepared installs from
the old source. Persist an application selection revision or equivalent explicit
request identity. Recheck it before starting a transfer, before installation and
before recording management after success. Stop/cancel old noncommitted requests
through the existing queue/session lifecycle and invalidate obsolete ready items.
Preserve unrelated queue rows.

If the platform install session has already been committed, defer source changes
until its actual outcome is reconciled; do not claim cancellation of a completed
or uninterruptible platform operation. A decline/failure of a proposed signature
replacement must not destroy the source preference that existed before the
replacement proposal or unrelated useful queue state.

Persist the library variant ID and immutable publication/artifact identity for
each request. Resume the exact request after process death, or explicitly resolve
a replacement publication; never silently mix an old base with new splits.
Existing saved RuStore/GitHub entries and backups must retain their identities.
Define a migration for any new queue fields and reject unsupported sources
explicitly rather than silently interpreting them as RuStore.

## Selecting a device variant

Keep the policy in a small testable Kotlin component. Build a device specification
from the target installation environment: SDK, supported process ABIs, available
features, OpenGL ES level, density and system language preferences. Do not select
by Pixel/OnePlus marketing name, profile ID or an asset filename.

Filter complete published variants by actual minimum SDK, usable native ABI,
required features/GL and complete required/provided split relationships. The
sampling SDK/ABI fields describe the request cohort, not a universal guarantee.
The actual archive-derived compatibility fields take precedence. Unknown or
unsupported critical compatibility data must yield an explicit unsupported
outcome rather than a best guess.

Among compatible complete sets, prefer the highest native versionCode. Resolve
ties deterministically using device ABI preference, a language match, density
suitability, then stable variant ID. Sample density need not exactly equal device
density: Android can scale available resources. Keep density differences visible
to the policy and test representative nonmatching densities. An available English
set may be used as the fallback language; do not promise additional languages.

Download every member of one selected verified variant, preserving its base and
split roles. Do not assemble a new cross-variant language/ABI set or drop a split
because it looks optional. No suitable set means installation is unavailable on
this device, with the reason and library link; it does not mean globally missing.
Do not advertise x86 support based on ARM translation observed on one emulator.

Language changes can require additional configuration APKs; installing a library
set does not guarantee automatic delivery of future languages or dynamic modules.
This release can select another complete published set through an explicit
reinstall action, while ordinary update checks still require a newer version.

## Update checks and installation

Fetch/revalidate the version feed once for a batch of library apps, sharing an
in-flight read and using HTTP validators and a disk cache. A scheduled update
check must revalidate rather than indefinitely trusting a fresh interactive cache.
For each app select the compatible variant, compare native versionCode with a
fresh installed-state read, and only queue a newer build. Never compare release
titles, upload dates, hash filenames or human version strings to decide freshness.

Before download and again after waiting to resolve an updated publication,
recheck the installed version and selected-source revision. If another store
already installed an equal/newer version, do not fetch the APK set. Preserve
existing explicit adoption/reinstall actions as separate requests that may
intentionally install the same version. No periodic APK downloads for inspection.

Reuse current metered-network consent, automatic download/install preferences,
queue pump, notifications, root/non-root routes and whole-APK limitations. Do not
extract only the base APK to work around a firmware refusal of split sessions.

Validate feed schema, package/variant membership, positive sizes, SHA-256 values
and immutable artifact references before downloading. Bind initial metadata and
asset URLs to the configured public library repository; validate permitted GitHub
asset-CDN redirects with the existing appropriate download route. Resolve relative
artwork paths beneath the library root, excluding traversal/arbitrary origins.
Verify every artifact's bytes/size, exact package/version, complete set and signer
continuity using existing checks plus library set identity. Do not weaken generic
GitHub or RuStore verification to accommodate the library.

Library publication removes old assets after switching the feed. On asset 404,
force-refresh the feed once. Retry only if the publication changed, rerun device,
source, signature and version decisions, discard incompatible partial state, and
stage a complete new set. Otherwise report a visible source failure. Do not loop,
fall back to another store, or downgrade because the feed changed.

Catalogue and version updates are independent. A metadata-only app may have a
visible card with no installable publication. A published app missing fresh
metadata may retain its cached card/details and continue version checks. Missing
app data and network failure are different states; neither deletes management.

## Proposed builder-owned defaults

The following are recommendations awaiting approval of this document:

- Library source enabled by default, independently of the existing GitHub switch.
- Editorial `foreign_category` boolean in the repository, default `true`.
- Package-based merging only for identities already known from their source.
- Preserve the existing unmanaged result ordering, with no silent source priority
  change; entry context selects the initial source for a new installation.
- Cached catalogue remains usable offline; manual refresh and explicit errors
  expose whether discovery or update checks could reach the source.
- Reuse existing details and TV interaction patterns rather than introducing a
  new destination or a user-facing variant picker in the initial release.

The builder chooses internal type names, cache expiry, storage layout, compatible
schema migrations and the precise selector control/accessibility semantics.
Those choices must satisfy the source, identity and race requirements above.

## Validation and acceptance

Use deterministic fixture-based tests for discovery and version-feed parsing,
source identity, package merging, legacy persistence, category default/exclusion,
localized metadata, unknown schema and malformed artifact references. Do not
depend on live Google or GitHub for unit tests.

Test variant selection across actual minSDK, ARM64/ARMv7, absent native libraries,
missing device features/GL, locales, differing densities, missing splits and
incompatible versions. Verify one whole set is selected and that highest/default
feed variant alone cannot force an unsupported device choice.

Test update and switch scenarios with a fake library server and download counters:
equal/older version fetches zero APK bytes; newer version fetches the required
set only; a competing store update before transfer also fetches zero; an update
during transfer is rechecked before installation. Exercise same-signature switch,
key rotation, incompatible/unknown signer, canceled replacement, queue/session
fences, process death, one-time 404 refresh and retained old management on failure.
Keep regression tests for existing custom GitHub release selection and updates.

Run `python tools/verify.py`, required lint checks for changed resources, and
`python tools/verify.py --release` before any installation. Localize new strings
in all eight shipped languages. In the library repository run its Python tests
and publication validation for the category-contract change.

On disposable emulators check fresh installs, updates, switching sources,
signer-changing replacement/cancellation, full split installation and phone/TV
navigation. Include a device density different from the sampled profile and a
supported older client Android version. Do not wipe the user's phone or Google
account emulator. Record exact ABI/native-bridge limits; an ARM translation crash
must not be presented as proof of failure on a real ARM phone, or as a success.

For ChatGPT and Grok, record installation, launch and sign-in separately using
the library set. A successful install is not proof that account access or service
features work. Ask for user assistance only when an interactive account sign-in
is actually required; do not publish credentials or bypass service checks.

## Design preparation evidence

The independent builder check raised one question about missing source signature
metadata. The clarification above resolves it: use existing RuStore/library
fingerprints first and preserve GitHub's existing APK-time verification. There
is no mandatory download or automatic source rollback solely for selection.

Before the documentation commit, the unchanged Android baseline was checked with
`python tools/verify.py`: `unit tests: 788 run, 0 failed, 0 errors, 0 skipped
(gradle exit 0)` and `VERIFY OK`. This verifies the baseline, not this proposed
feature. No feature code, release APK or device installation was produced.

## References

- [Library implementation contract](https://github.com/wyrtensi/wystore-library/blob/main/docs/device-variants-contract.md).
- Existing Android integration points: `data/Models.kt`, `data/SearchSources.kt`,
  `data/CuratedCategories.kt`, `data/CatalogRepository.kt`, `data/AdoptionPolicy.kt`,
  `data/SigningVerifier.kt`, `StoreViewModel.kt`, category/details/search UI,
  `background/UpdateCheckWorker.kt`, `background/UpdateDownloadWorker.kt`,
  `updates/QueueRepository.kt` and `updates/InstallSessionWriter.kt`.
- [Android App Bundle format](https://developer.android.com/guide/app-bundle/app-bundle-format):
  base/configuration APKs and additional language delivery.
- [Support different pixel densities](https://developer.android.com/training/multiscreen/screendensities):
  Android selects available density resources and can scale them.

After the operator approves this design, write the implementation plan using
`superpowers:writing-plans`. Approval of the design is not a release request.
