# Message style guide

The built-in messages follow a simple writing style (ASD-STE100 "Simplified Technical English"): short, clear, same look everywhere.

## Rules
* Maximum 20 words per sentence.
* Active voice, present tense. One instruction per sentence.
* No slang, no CAPITAL LETTERS for shouting, no "please".
* Keep every `{placeholder}`.

## Colours (Minecraft `&` codes)
| Code | Use |
|---|---|
| `&8[&6&lAntiBadWords&8]&r ` | the prefix of every message |
| `&6` gold | brand, headings |
| `&b` aqua | information, input prompts |
| `&a` green | success |
| `&e` yellow | warning, notice |
| `&c` red | only the key word of a block, penalty or error |
| `&7` gray | normal text |
| `&f` white | values (names, numbers, times) |
| `&l` bold | key words |
| `&o` italic | hints and the offending word |

Each code ends the previous formatting, so the text after a bold key word starts with `&7`.

## Before and after
* Warning: `&EWATCH YOUR WORDS. YOU ARE PUNISHED IF YOU SAY &F{REMAINING}&E BADWORDS AGAIN.`
  becomes `&8[&6&lAntiBadWords&8]&r &e&lWarning. &7Your message contains a word that is not permitted. After &f{remaining}&7 more violations, a penalty is applied.`
* Penalty: `FOR YOUR BADWORDS, YOU ARE PUNISHED WITH {PUNISHMENTS}.`
  becomes `... &c&lPenalty. &7You used too many words that are not permitted. The penalty is: &f{punishments}&7.`
* Mute: `You are muted. Time left: {remaining}. Reason: {reason}`
  becomes `... &c&lMuted. &7You cannot write now. Time left: &f{remaining}&7. Reason: &f{reason}&7.`
* Command error: `No permission.` becomes `[AntiBadWords] Error. No permission.`

## For upgrades
When you upgrade, a message text that is still exactly the old default is replaced by the new default (one info line in the log). **A text you changed is never touched.** Comments in the file stay. To get the new text, delete your line or copy it from [CONFIGURATION.md](CONFIGURATION.md).

## Other texts
* **Log lines** (console) use the same sentences without colour codes: `Area: error. The plugin cannot ...` (key word first, one fact per sentence).
* **Discord** texts are plain text, no `&` codes, markdown safe. Webhook templates (`discord.templates.*`) are sentences without a prefix (the sender name and the card title show it).
  Bot replies start with `[AntiBadWords] ` and then `Error.`, `Notice.`, `Info.` or `Done.`. A value that a player typed is escaped before it is inserted.
* **GUI**: item names are gold, lore lines are gray (set where the item is drawn). Results of a click (saved, error, discarded) use the standard prefix. Titles are short because of the 32-character limit of old Bukkit versions.
* Every changed default is recorded in `MessageDefaults.OLD_DEFAULTS`, so an unchanged install gets the new text.

## Not restyled
`mute.no-reason-text` and the example commands in the punishment section.
