# Session 1/5 (easy): Word lists for 35 languages

## Summary
AntiBadWords currently ships bad-word lists for 7 languages inside `config.yml`. Expand this boldly to **35 languages (37 files)** as
one data file per language, with a documented format, source credits, and a validator. This session is **data and tooling only**: it
must not change any Java, Gradle or Maven code, so it can run in parallel with session 2 without merge conflicts. Session 2 writes the
loader that reads these files. Read `handoffs/00-shared-context.md` first (rules, tiers, config layout).

## Tasks
1. Create the top-level folder `7-wordlists/` (ELI5 README: what a word list is, how to add or edit a language, how tiers work, what
   `review-status` means, how to ask a native speaker to review).
2. Define and document the file format, one file per language, `7-wordlists/<code>.yml`:
   ```yaml
   language: id                      # same as the file name
   name: Indonesian (Bahasa Indonesia)
   script: latin                     # latin | cyrillic | arabic | han | hangul | kana | thai | devanagari | bengali | greek | hebrew | ...
   review-status: unreviewed         # unreviewed | community-reviewed | native-reviewed
   sources:
     - "LDNOOBW, CC BY 4.0, https://github.com/LDNOOBW/List-of-Dirty-Naughty-Obscene-and-Otherwise-Bad-Words"
     - "AI-written, unreviewed"
   notes: "short free text, for example known false-positive risks"
   level-1: [ ... ]                  # mild insults ("stupid")
   level-2: [ ... ]                  # rude / vulgar
   level-3: [ ... ]                  # severe: strong profanity, sexual terms, slurs
   ```
   Words are lower-case, NFC-normalised, one entry per list item; multi-word entries allowed ("fick dich"); no entries shorter than 3
   characters for alphabetic scripts (2 for Han/Hangul/Kana/Thai); no duplicates inside a file.
3. Create these files (code: language). Keep **all words from the old lists** in `config.yml` (`word-lists:` section, 7 lists) as a
   superset: `en` English, `en-gb` UK English extras only, `id` Indonesian, `ms` Malay, `zh` Chinese Simplified (+ pinyin), `zh-tw` Chinese
   Traditional / Cantonese (+ jyutping/romanisation), `ru` Russian (Cyrillic **and** Latin transliteration), `ko` Korean (hangul + romanisation),
   `ja` Japanese (kana/kanji + romaji), `es` Spanish, `pt-br` Portuguese (Brazil), `pt-pt` Portuguese (Portugal), `fr` French, `de` German,
   `it` Italian, `nl` Dutch, `tr` Turkish, `ar` Arabic (+ Franco-Arab/Latin chat spelling), `hi` Hindi (Devanagari + Hinglish), `bn` Bengali,
   `ur` Urdu (+ Roman Urdu), `vi` Vietnamese (with and without diacritics), `th` Thai (+ romanisation), `fil` Filipino/Tagalog, `pl` Polish,
   `uk` Ukrainian (+ transliteration), `sv` Swedish, `no` Norwegian, `da` Danish, `fi` Finnish, `el` Greek (+ Greeklish), `he` Hebrew,
   `fa` Persian (+ Finglish), `ro` Romanian, `cs` Czech, `hu` Hungarian, `sw` Swahili. Also create an empty `custom.yml` (admin's own words,
   same format, `language: custom`).
4. Sourcing: start from openly licensed public lists. The **LDNOOBW** project (CC BY 4.0, covers about 28 of these languages) is the first
   candidate: open its `LICENSE` and **verify the licence yourself** before copying anything. Do **not** copy any list whose licence is
   missing, unclear or incompatible with MIT. Credit every source with URL, licence and retrieval date in `7-wordlists/NOTICE.md`. If the
   network blocks you, write the lists yourself and say so in `sources`. Fill gaps with your own knowledge, aiming for 60-200 useful words per
   language across the three tiers (fewer is fine where a language has fewer common insults; quality over volume). Include the romanised /
   chat spellings people really type, because players rarely use native scripts for insults.
5. **False-positive discipline** matters more than volume: avoid words that are common innocent words in the same language or in other
   languages (for example a Scandinavian word that means "speed" but is an English insult). Put risky words in the file's `notes`, or leave
   them out. Do not include short ambiguous words in tier 1.
6. Write `7-wordlists/tools/validate.py` (Python 3 standard library only; YAML parsing may use a tiny built-in parser or `yaml` if available,
   but the script must also work without PyYAML by falling back to a simple reader for this restricted format). It checks: required keys, file
   name equals `language`, tiers present, NFC normalisation, lower-case, no duplicates inside a file, min lengths, script sanity (for example Han
   words contain Han characters), allowed `review-status`, and **cross-language collisions** (same word in two languages, printed as a report;
   collisions between a language and an English common-word list are errors unless listed in that file's `notes`). Exit code non-zero on errors.
7. Write `7-wordlists/tools/stats.py` that prints a table: language, script, review-status, words per tier, total.
8. Add a short section to the root `README.md` and `6-guides/README.md` pointing to `7-wordlists/`, and update the folder map in `README.md`.
   Do not touch any other file.

## Files
Create: `7-wordlists/README.md`, `7-wordlists/NOTICE.md`, `7-wordlists/<code>.yml` (37 files + `custom.yml`), `7-wordlists/tools/validate.py`,
`7-wordlists/tools/stats.py`. Modify only: `README.md` (folder map), `6-guides/README.md`. Read for context: `2-plugin-bukkit-purpur/src/main/resources/config.yml`
(the 7 legacy lists), `6-guides/CONFIGURATION.md`.

## Constraints
* No Java/Gradle/Maven/script changes; sessions 2-5 own the code. Session 2 will wire `7-wordlists/` into the jars and the loader, so keep the format exactly as above.
* Every list starts as `review-status: unreviewed` (AI-written or machine-assisted lists are never marked reviewed).
* Never put secrets or personal data in files. Do not commit downloaded raw third-party repos; commit only the derived lists plus `NOTICE.md`.
* Do not enable all languages by default: that is a config decision for session 2 (default stays English + the admin's custom list).

## Acceptance criteria
- `python3 7-wordlists/tools/validate.py` exits 0 with no errors; collisions are reported and each is either removed or justified in `notes`.
- `python3 7-wordlists/tools/stats.py` shows 37 language files with non-empty tiers (at least tier 2 and 3 for every language).
- All legacy words from the 7 old lists are present in the matching new files (write a quick check and mention the result).
- `NOTICE.md` lists every external source with URL, licence and date; the README explains the format in plain words.
- `git diff --stat` shows only the files listed above.

## Branch
`handoff/session-1-languages`, branched from `main`. Open a pull request to `main` when done. This session can run in parallel with session 2.

## Report back
What changed (file count, words per language table from `stats.py`), which sources you used with their licences, languages that are thin or
risky and why, collision report summary, anything you could not verify, and suggested native-speaker review priorities.
