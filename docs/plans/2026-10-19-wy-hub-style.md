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

## Global

- [x] Palette, Manrope, radii, hairline (theme)
- [x] Bottom bar: capsule, tab pill holding icon and name, one label size for all
- [ ] Faint tags for version / installer / section
- [ ] Snackbar and the "waiting for Android" banner over the last rows
- [ ] Dialogs: what's new, metered network, unverified source, adoption, GitHub install, replace,
      force Wy Store, offer next, source failure

## Updates

- [x] Panel: all current / ready N (Install all) / in queue / unreachable; check ring
- [x] Ready to install as one group; accepted apps as one group
- [ ] Queue cards: queued, resolving, downloading, paused, verifying, awaiting Android's dialog,
      canceled, failed (network, signature with replace, unverified source, source problem)
- [ ] Check running card; problem lines under the panel
- [ ] Empty: nothing accepted

## Home

- [ ] Header and the updates panel: nothing pending / N ready / queue running
- [ ] Catalogue loading, failed (retry), stale
- [ ] Categories rail, GitHub rail (installed mark, GitHub switched off)
- [ ] RuStore list as one group; row states: not installed, installed, update, downloading,
      paused, ready, installing, failed, unverified source

## Search

- [ ] Start state, sources chips, the search button and the note
- [ ] Results: RuStore group, GitHub group; loading; nothing found; source error / rate limit;
      load more
- [ ] Inline progress and failure on a result

## My apps

- [ ] Header, search field, filter chips
- [ ] Accepted and not accepted as groups; collapse
- [ ] Expanded row: facts, auto-update and force switches, frozen, missing from RuStore,
      update available, actions
- [ ] Selection: long press, select all, accept many, batch progress
- [ ] Backup nudge, export, import
- [ ] Empty states: search, all accepted, empty filter

## App page (RuStore) and GitHub page

- [ ] Not installed, installed and current, update, downloading, paused, ready, awaiting dialog,
      failed
- [ ] Takeover in place, replace (signature / older in catalogue), source unconfirmed
- [ ] Incompatible Android, no root note
- [ ] Facts, what's new, description, screenshots viewer, reviews and "load all"

## Settings

- [ ] Hub list as groups
- [ ] Appearance, language, notifications, sources, GitHub repositories, about (self-update,
      diagnostics), permission center, background updates
