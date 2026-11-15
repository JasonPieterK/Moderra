# NOTICE - where the word lists come from

Retrieval and check date for everything below: **2026-11-12**.

## Summary

All 37 language files were **written by an AI from its own general knowledge**, plus the old word lists of Moderra 1.x
(the `word-lists:` section of the plugin's `config.yml`, which is part of this project and under the project's licence (all rights reserved, see LICENSE)).
Every file is marked `review-status: unreviewed`: no human has checked any of them.

No third-party list was copied into these files.

## External sources that were looked at

### LDNOOBW - List of Dirty, Naughty, Obscene, and Otherwise Bad Words

* URL: https://github.com/LDNOOBW/List-of-Dirty-Naughty-Obscene-and-Otherwise-Bad-Words
* Copyright: (c) 2012-2020 Shutterstock, Inc.
* Licence: **Creative Commons Attribution 4.0 International (CC BY 4.0)**. The licence text in the repository's `LICENSE` file and the
  statement in its `README.md` were read on 2026-11-12 and confirm this.
* What was done: the language lists were downloaded once to a temporary folder to check the licence and the size of the lists.
  **No entry was copied** into this repository, and the temporary files were not committed. The files of the languages that LDNOOBW
  covers mention it in their `sources` only as the reference that was checked for its licence.
* If a later change copies words from it, the attribution required by CC BY 4.0 (name, link to the licence, note of changes) goes here
  and the copied entries must be marked in the file. CC BY 4.0 material may be combined with this project, but the copied data keeps its
  CC BY 4.0 terms.

## Look-alike letter table (not a word list)

`5-shared-engine/src/main/resources/moderra-defaults/look-alikes.yml` (copied into every jar and, as the `look-alikes:` part
of `filters/confusables.yml`, into every config folder) is **derived from Unicode data**:

* Source: `confusables.txt`, version 18.0.0 (dated 2026-08-06), the data file of Unicode Technical Standard #39
  "Unicode Security Mechanisms", (c) Unicode, Inc. Official location: <https://www.unicode.org/Public/security/latest/confusables.txt>.
  It was downloaded from the copy in the ICU repository (`unicode-org/icu`, `icu4c/source/data/unidata/confusables.txt`),
  because unicode.org could not be reached from where this was built.
* Licence: **Unicode License v3** (<https://www.unicode.org/license.txt>), a permissive licence that allows use, copying,
  modification and distribution of the data files provided the copyright and permission notice is kept with the data or
  its documentation. This section is that notice: *Copyright (c) 1991-2026 Unicode, Inc. All rights reserved.
  Distributed under the Terms of Use in https://www.unicode.org/copyright.html.*
* What was changed: only lines "one letter of another alphabet -> one plain Latin letter" were kept (Cyrillic, Greek,
  Armenian, Coptic, Cherokee, Lisu, Georgian, Hebrew, Arabic; 221 entries), lower-cased, with capital look-alikes of "I"
  written as `i`. The script that does this is `5-shared-engine/generate-confusables.py`.
* Added by hand (not from Unicode): the four Cyrillic letters version 1 of the plugin already treated as look-alikes
  (`к м н т`) and the group `always` (26 Latin small-capital and phonetic letters).

## What this means

* Quality: the lists are first drafts. Tier assignment, spelling variants and dialect coverage can be wrong.
  The thinnest or riskiest languages are named in the `notes` of each file (for example `sw`, `he`, `th`, `vi`, `fil`).
* Safety of use: the lists are filters, not a statement about people. Some entries are slurs and are listed only so they can be blocked.
* Please send corrections: open an issue or a pull request, and add yourself or your group to the `sources` of the file you reviewed.
