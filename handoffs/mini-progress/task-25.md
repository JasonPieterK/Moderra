# Task 25: word-list risks (thin lists, false positives)

Branch `mini/25`. Data, validator and engine tests only (one change to a bundled default).

## What was added
* `WordListFalsePositiveTest` (engine tests): loads every real `7-wordlists/*.yml` at once into a real `Engine` (all 37 languages switched on,
  default matching options, the bundled whitelist, the evasion normaliser) and runs `src/test/resources/falsepositive/corpus-1..4.txt`
  (format `<code>|<sentence>`) at protection levels 1, 2 and 3. At least 15 everyday sentences for each of the 37 languages, plus a `misc` group
  (names, places such as Scunthorpe/Penistone/Essex/Sussex, Minecraft vocabulary) and stress sentences for Han/kana/Thai substring matching and
  accent-folding collisions. Any hit fails. `-DargLine="-Dabw.fpreport=<file>"` writes the failures as UTF-8.
* Thin lists extended (still `unreviewed`): `sw` 22 -> 56, `fil` 39 -> 90, `he` 55 -> 96, `bn` 65 -> 103 words.
* Bundled default whitelist (new installs only; migrated installs keep their own whitelist): added `suka`, `hui`, `burro`, `tai`, `lixo`.
* `7-wordlists/README.md`: review priorities, the test, "how to report a false positive".

## False positives found and fixed (all documented in the `notes` of the file)
* Folding collisions: cs `dům` -> `dum` (removed `dum` from da/no/sv), cs `píča`/pt-pt `piça` -> `pica` (Spanish verb), es `piña` -> hu `pina`.
* Thai folds tone marks away: removed ร่าน/อีร่าน (ร้าน shop), เหี้ย alone (เหยียบ), แม่ง alone (แมงมุม), กะหรี่ alone (curry), อีหี (ซื้อหนังสือ), ควาย alone; added longer forms.
* Chinese: removed 妈的, 他妈, 我操, 我日, 去死 (zh); 媽的, 他媽, 你媽, 三小, 豬頭, 低能, 去死, 廢物 (zh-tw); longer forms added.
* Japanese: removed hiragana ばか (inside ばかり), ぐず, くそ (inside ふくそう); longer forms added.
* Other: fr `bite`, da `nar`, fa `kuni`, ko `보지`/`자지`, it `fica`, es `joto`, bn `boka`, pl `jeb`, en-gb `knob`, id `babi`/`monyet`(standalone)/`pea`/`coli`.

## Open risks (not fixable with data only)
* The whitelist is NOT applied to Han, kana and Thai text (WordMatcher step 4 ignores it). Legacy zh entries 你妈 (in 你妈妈), 废物, 弱智, 尼玛,
  杂种 therefore stay as documented risks. Suggested later engine change: apply the whitelist to the unspaced path.
* Kana entries are found across punctuation (tokens are joined): `さあ、ほら` contains あほ.
* Known small risks kept on purpose (in the notes): en `tits` `cum` `jerk` `dick`, `cock` and `penis` (legacy, no whitelist), pt-br `anta`, ko `새끼`,
  id `asu` (legacy, main Javanese insult; also Japanese romaji "tomorrow"), sw `kuma` (Japanese romaji "bear").
* `babi` and `monyet` were not in the v1 legacy config (the brief listed them); standalone forms were removed, insult phrases kept.
