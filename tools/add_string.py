#!/usr/bin/env python3
"""Add new UI strings to every language at once.

    python tools/add_string.py strings.json
    python tools/add_string.py strings.json --replace     # overwrite keys that already exist

strings.json maps each key to its text in every language the app ships, by the suffix of the
values folder ("en" for the default one):

    {
      "library_select": {"en": "Select", "ru": "Выбрать", "be": "Выбраць", "uk": "Вибрати",
                         "kk": "Таңдау", "lv": "Atlasīt", "uz": "Tanlash", "zh": "选择"}
    }

Nothing is written unless every key has text for every language: a missing translation would
fall back to English on that phone, and TranslationParityTest fails the build on it anyway.
Escaping for Android string resources (&, <, >, apostrophes, double quotes) is done here; write
the text as it should read. Keep format arguments (%1$s, %2$d) the same in every language.
"""
import argparse
import json
import re
import sys
from pathlib import Path
from xml.sax.saxutils import escape

RES = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "res"


def languages() -> dict[str, Path]:
    found = {}
    for folder in sorted(RES.glob("values*")):
        strings = folder / "strings.xml"
        if strings.exists():
            code = "en" if folder.name == "values" else folder.name.removeprefix("values-")
            found[code] = strings
    return found


def encode(text: str) -> str:
    # Android reads a bare apostrophe or double quote as markup in a string resource.
    return escape(text).replace("'", "\\'").replace('"', '\\"')


def tokens(text: str) -> list[str]:
    return sorted(re.findall(r"%\d+\$[sd]", text))


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("file", help="JSON file: {key: {lang: text}}")
    parser.add_argument("--replace", action="store_true", help="overwrite keys that already exist")
    args = parser.parse_args()
    sys.stdout.reconfigure(encoding="utf-8")

    entries = json.loads(Path(args.file).read_text(encoding="utf-8"))
    langs = languages()
    problems = []
    for key, texts in entries.items():
        if not re.fullmatch(r"[a-z][a-z0-9_]*", key):
            problems.append(f"{key}: not a valid resource name")
        missing = sorted(set(langs) - set(texts))
        if missing:
            problems.append(f"{key}: no text for {', '.join(missing)}")
        reference = tokens(texts.get("en", ""))
        for lang, text in texts.items():
            if lang not in langs:
                problems.append(f"{key}: unknown language {lang}")
            elif tokens(text) != reference:
                problems.append(f"{key}/{lang}: format arguments {tokens(text)} differ from en {reference}")
    if problems:
        print("nothing written:")
        for p in problems:
            print("  " + p)
        sys.exit(1)

    for lang, path in langs.items():
        content = path.read_text(encoding="utf-8")
        for key, texts in entries.items():
            line = f'    <string name="{key}">{encode(texts[lang])}</string>'
            existing = re.compile(rf'^\s*<string name="{key}"[^>]*>.*?</string>\s*$', re.M)
            if existing.search(content):
                if not args.replace:
                    print(f"nothing more written: {key} already exists in {path.parent.name} (use --replace)")
                    sys.exit(1)
                content = existing.sub(line, content, count=1)
            else:
                end = content.rindex("</resources>")
                content = content[:end] + line + "\n" + content[end:]
        path.write_text(content, encoding="utf-8")
    print(f"{len(entries)} string(s) written to {len(langs)} languages: {', '.join(langs)}")


if __name__ == "__main__":
    main()
