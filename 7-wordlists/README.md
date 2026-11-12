# 7-wordlists - the bad-word lists

This folder holds the lists of words that AntiBadWords blocks. One file per language.

**Heads up:** these files contain swear words. That is the whole point, but please do not read them out loud in class.

## What is a word list?

A word list is a plain text file with words in it. When a player types a word that is on the list, AntiBadWords can block or punish it.
Every language has its own file, for example `de.yml` for German and `id.yml` for Indonesian.

* 37 language files (35 languages; English and Portuguese and Chinese have two variants each)
* `custom.yml` is empty on purpose. It is for **your** words.

Nothing in here switches on by itself. Which languages are active is a setting in the server config (a later part of the project wires that up).
The plan is: English plus your custom list is the default.

## The three tiers (how strong is the word?)

| Tier | Meaning | Example idea |
|------|---------|--------------|
| `level-1` | mild insults | "stupid" |
| `level-2` | rude or vulgar | "bastard" |
| `level-3` | severe: strong swearing, sexual words, slurs | the worst stuff |

The server's protection level picks what is used:

* protection level 1 keeps **only tier 3**
* protection level 2 keeps **tiers 2 and 3**
* protection level 3 keeps **all tiers**

## The file format

Every file looks like this:

```yaml
language: "id"                    # same as the file name (the quotes matter, see below)
name: Indonesian (Bahasa Indonesia)
script: latin                     # latin | cyrillic | arabic | han | hangul | kana | thai | devanagari | bengali | greek | hebrew
review-status: unreviewed         # unreviewed | community-reviewed | native-reviewed
sources:
  - "where the words came from"
  - "AI-written, unreviewed"
notes: "short free text, for example known false-positive risks"
level-1: ["bodoh", "tolol"]
level-2: ["bangsat", "bajingan"]
level-3: ["kontol", "memek"]
```

Rules for the words:

* all lower-case, written in the normal Unicode form (NFC)
* one entry per list item; entries with spaces are allowed ("fick dich")
* at least 3 letters for alphabet scripts, at least 2 for Chinese, Japanese, Korean and Thai
* no duplicates inside a file (a word lives in exactly one tier)
* Latin spellings that players really type are welcome in non-Latin files (for example `blyat` in `ru.yml`, `ssibal` in `ko.yml`)
* put the value of `language:` in double quotes. Without quotes, YAML reads the Norwegian code `no` as the word "no" (false)!

How the matcher works (so you know what to put in): a typed word is blocked only when it **equals** a list entry. Accents are removed first
(so `coño` and `cono` are the same). Chinese, Japanese and Thai have no spaces between words, so those entries are found inside longer text.
That means short Chinese, Japanese or Thai entries can hit innocent sentences. Keep them rare and read the `notes` of the file.

### Special markers inside `notes`

The checker script reads these:

* `short-ok: fk mf` - entries that are allowed to be shorter than the minimum (only for old words we must keep)
* `collisions-ok: de fr idiot` - other language codes, or words, that this file shares on purpose with another file

## `review-status` - who checked this list?

| Value | Meaning |
|-------|---------|
| `unreviewed` | written by an AI (or machine-assisted), **no human has checked it**. Every list starts here. |
| `community-reviewed` | several players or admins who speak the language read it and agree |
| `native-reviewed` | a native speaker read it line by line and fixed mistakes |

Never change the value unless a real human really did the review.

## How to add or edit a language

1. Copy a small file, for example `custom.yml`, to `<code>.yml` (use the ISO code such as `sv`).
2. Fill in `language`, `name`, `script`, `sources`, `notes` and the three tiers.
3. Run the checker: `python3 7-wordlists/tools/validate.py`. It must print `0 errors`.
4. Run `python3 7-wordlists/tools/stats.py` to see the numbers.
5. Add your source to `NOTICE.md`.

To add a single word, put it in the right tier of the file and run the checker again.

### False positives matter more than volume

A word list that blocks normal sentences is worse than a short list. Before you add a word ask: *is this also a normal word in this language
or in another language?* (Example: the Swedish word for "speed" is an English insult.) If yes, leave it out or mention it in `notes`.
Do not put short ambiguous words in tier 1.

## The tools

| Tool | What it does |
|------|--------------|
| `python3 7-wordlists/tools/validate.py` | checks every file: required keys, file name, tiers, lower-case, NFC, duplicates, lengths, script sanity, English common words, and prints a report of words shared by several languages. Exit code is not 0 when something is wrong. Use `--strict` to also fail on unexplained shared words. |
| `python3 7-wordlists/tools/stats.py` | prints a table: language, script, review status, words per tier, total |

Both need only Python 3 (no extra packages). If PyYAML is installed, `validate.py` also checks that PyYAML reads the files the same way.

## How to ask a native speaker to review a list

Send them the file (or a screenshot of the three lists) and ask:

1. Which words are **not** really swear words or insults? (We will remove them.)
2. Which words are normal everyday words that could be blocked by mistake?
3. Which common insults, spellings or chat abbreviations are missing?
4. Is each word in the right tier (mild, rude, severe)?

Then fix the file, run the checker, and only then set `review-status` to `community-reviewed` or `native-reviewed`. Add their name or
group to `sources` if they agree to it.

## Where this folder goes

This folder is the source of truth. A later part of the project copies these files into every jar and into the config folder on first start.
