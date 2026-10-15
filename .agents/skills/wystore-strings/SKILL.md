---
name: wystore-strings
description: Add or change user-visible text in Wy Store in every language it ships at once. Use whenever a change adds a label, button, message, dialog text, description or error the user sees, or rewords an existing one.
---

# Strings in every language

Wy Store ships eight languages: English (`values`), Russian, Belarusian, Ukrainian, Kazakh,
Latvian, Uzbek and Chinese (`values-ru`, `-be`, `-uk`, `-kk`, `-lv`, `-uz`, `-zh`). A new string
goes into all of them in the same change. A missing one falls back to English on that phone, and
`TranslationParityTest` fails the build.

## How

1. No literal user-visible text in Kotlin. Everything goes through `stringResource(R.string.x)`
   (Compose) or `getString` (view model, workers). Brand and store names (Google Play, RuStore,
   GetApps, GitHub, Wy Store) stay as they are and may be literals.
2. Write the strings to a JSON file and add them in one go:

   ```json
   {
     "library_select": {"en": "Select", "ru": "Выбрать", "be": "Выбраць", "uk": "Вибрати",
                        "kk": "Таңдау", "lv": "Atlasīt", "uz": "Tanlash", "zh": "选择"}
   }
   ```

   ```
   python tools/add_string.py new_strings.json            # refuses if any language is missing
   python tools/add_string.py new_strings.json --replace  # reword existing keys
   ```

   The script escapes `&`, `<`, `>`, apostrophes and double quotes; write the text as it should read.
   It refuses keys whose format arguments (`%1$s`, `%2$d`) differ between languages.
3. A string nothing uses any more is removed from all languages.
4. `python tools/verify.py` - parity is part of the unit tests.

## Writing them

- Russian is the primary audience; write it first and natively, not as a translation of English.
  The other languages are translated from it, keeping meaning over word order.
- Say what happens and what the user can do, in the words the rest of the app uses: «Принять»,
  «Принятые», «Не принятые», «Убрать», «Установщик», «Обновления берутся из». Check existing
  strings in `values-ru/strings.xml` before inventing a term.
- Short where space is short: chips, badges and buttons must fit the compact phone at font scale
  1.15. Long text belongs in a description line, not on a button.
- Uzbek uses the modifier letter `ʻ` (U+02BB) in oʻ / gʻ and `’` in loanword apostrophes, as the
  existing strings do.
- After adding, look at the screen in at least Russian or English on a device when the text is new
  UI (`wystore-device`).
