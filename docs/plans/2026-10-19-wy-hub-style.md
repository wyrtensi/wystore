# Wy Hub style on the phone - screens and the states each must survive

Branch `design/wy-hub-style`. Rules taken from the Wy Hub concept (wy-hub-legacy,
`app/src/main/assets/wyhub_pencil`):

- No top bar: an accent eyebrow over a large plain title, a grey glass pill at the right.
- A screen opens with one glass panel that says the state of things in a sentence.
- Lists are rows of one panel divided by hairlines, not a card per item; sections keep their gap.
- Secondary facts (version, installer, section) sit in faint tags; status words are coloured text.
- Wide grey pill for "open ...", the accent fill only for the step the user is expected to take.
- Every check below is done in dark and light, on the regular phone and the compact one
  (sw393dp, font scale 1.15), in Russian, and once in Kazakh for the system-font fallback.

`[~]` restyled in code, not yet looked at on a device in every state listed.

## Global

- [x] Palette, Manrope, radii, hairline (theme)
- [x] Bottom bar: capsule, tab pill holding icon and name, one label size for all
- [x] Faint tags for version / installer / section
- [x] Snackbar and the "waiting for Android" banner over the last rows (one capsule each, same width)
- [~] Dialogs: what's new, metered network, unverified source, adoption, GitHub install, replace,
      force Wy Store, offer next, source failure

## Updates

- [x] Panel: all current / ready N (Install all) / in queue / unreachable; check ring
- [x] Ready to install as one group; accepted apps as one group
- [x] Queue cards: queued, resolving, downloading, paused, verifying, awaiting Android's dialog,
      canceled, failed (network, signature with replace, unverified source, source problem)
- [x] Check running: inside the panel (live line and ring); problem lines under the panel
- [ ] Empty: nothing accepted

## Home

- [x] Header and the updates panel: nothing pending / N ready / queue running
- [x] Catalogue loading, failed (retry), stale; a section is one panel of rows, page failure has Retry
- [x] Categories rail, GitHub rail (installed mark, GitHub switched off)
- [x] RuStore list as one group; row states: not installed, installed, update, downloading,
      paused, ready, installing, failed, unverified source

## Search

- [x] Start state, sources chips, the search button and the note
- [x] Results: RuStore group, GitHub group; loading; nothing found; source error ("no connection");
      load more; GitHub switched off searches RuStore alone
- [ ] Inline progress and failure on a result

## My apps

- [x] Header, search field, filter chips
- [x] Accepted and not accepted as groups; collapse
- [~] Expanded row: facts, auto-update and force switches, frozen, missing from RuStore,
      update available, actions
- [x] Selection: select all, accept; batch progress as a panel with a ring (seen in code, not on a device)
- [ ] Backup nudge, export, import
- [x] Empty states: search, empty filter

## App page (RuStore) and GitHub page

- [~] Not installed, installed and current, update, downloading, paused, ready, awaiting dialog,
      failed
- [~] Takeover in place, replace (Google-installed seen); older in catalogue, source unconfirmed not seen
- [x] Incompatible Android (first note, red), no root note
- [~] Facts, what's new, description, screenshots viewer, reviews and "load all"

## Settings

- [x] Hub list as groups
- [x] Appearance, language, notifications, sources, about (self-update, diagnostics), permission
      center, updates and storage sub-pages
- [x] Kazakh on the system font (Android 9, light)

## Done so far (branch)

- Status tones in one place (StatusTone): active accent, ready green, paused yellow, asking blue,
  failed red sentence, neutral grey.
- Tabs renamed in Russian: «Апдейты», «Приложения».
- Rails bleed to the screen edge; lists under a fixed header fade at the top.

## Still open

- Search results cannot tell an app needs a newer Android (the search answer has no minSdk), so a
  row still offers Install; the page explains it.
- Inline progress and failure on a search result; backup nudge, export and import; "nothing
  accepted" on Updates; takeover when the catalogue is older or the source is unconfirmed.
