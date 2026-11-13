#!/usr/bin/env python3
"""Builds the default look-alike table (src/main/resources/abw-defaults/look-alikes.yml) from Unicode's confusables.txt.

Usage:  python3 -I generate-confusables.py /path/to/confusables.txt > src/main/resources/abw-defaults/look-alikes.yml

confusables.txt is the data file of Unicode Technical Standard #39 (https://www.unicode.org/Public/security/latest/,
also mirrored in the ICU repository). It is (c) Unicode, Inc. and used under the Unicode License v3
(https://www.unicode.org/license.txt); see 7-wordlists/NOTICE.md.

What is kept: lines "one character -> one plain Latin letter", where the character is a letter that is not already
turned into a plain letter by Unicode compatibility folding plus removing accents (the plugin does that itself).
A capital look-alike of "I" is written as i (Unicode folds I to l); everything else is lower-cased.
"""
import sys
import unicodedata

SCRIPTS = ["CYRILLIC", "GREEK", "ARMENIAN", "COPTIC", "CHEROKEE", "LATIN", "LISU", "GEORGIAN", "HEBREW", "ARABIC"]
# Kept from version 1 of the plugin (lower-case Cyrillic letters that Unicode does not list as look-alikes).
EXTRA = {"cyrillic": [("к", "k"), ("м", "m"), ("н", "h"), ("т", "t")]}


def folds_to_ascii(ch):
    out = "".join(c for c in unicodedata.normalize("NFKD", ch) if not unicodedata.category(c).startswith("M"))
    return out != "" and all(ord(c) < 128 for c in out)


def main(path):
    table = {}
    for line in open(path, encoding="utf-8-sig"):
        line = line.split("#")[0].strip()
        if not line:
            continue
        parts = [p.strip() for p in line.split(";")]
        src, dst = parts[0].split(), parts[1].split()
        if len(src) != 1 or len(dst) != 1:
            continue
        s, d = chr(int(src[0], 16)), chr(int(dst[0], 16))
        if not (d.isascii() and d.isalpha()) or s.isascii():
            continue
        if not unicodedata.category(s).startswith("L") or folds_to_ascii(s) or s in "\"\\'":
            continue
        name = unicodedata.name(s, "")
        script = next((x for x in SCRIPTS if name.startswith(x)), None)
        if script is None:
            continue
        target = "i" if d == "l" and unicodedata.category(s) == "Lu" else d.lower()
        table.setdefault(script.lower(), []).append((s, target))
    for script, pairs in EXTRA.items():
        known = {s for s, _ in table.get(script, [])}
        table.setdefault(script, []).extend(p for p in pairs if p[0] not in known)
    for script in [s.lower() for s in SCRIPTS]:
        pairs = sorted(table.get(script, []), key=lambda p: (p[1], ord(p[0])))
        if not pairs:
            continue
        print(script + ":")
        row = []
        for s, t in pairs:
            row.append(s + "=" + t)
            if len(row) == 12:
                print('  - "' + " ".join(row) + '"')
                row = []
        if row:
            print('  - "' + " ".join(row) + '"')


if __name__ == "__main__":
    main(sys.argv[1])
