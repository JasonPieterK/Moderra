# Task 31: message redesign (ASD-STE100 style)

Verification: engine unit tests run (`mvn -q -B test` green, 375 tests). No stub compile needed (engine only).

## Added
- `engine/config/MessageDefaults`: `OLD_DEFAULTS` (setting key -> earlier default texts) and `upgrade(...)`, called in `Engine.reload()`
  after the defaults are written. A message equal to an old default is replaced by the new default (comments stay, one info log line).
  A customised message is never touched. For a later text change: add the replaced text to `OLD_DEFAULTS`.
- `command/Chat`: palette constants (AQUA, BOLD, ITALIC, RESET, PREFIX) and helpers `ok`, `error`, `warn`, `info`, `usage`, `heading`, `hint`, `val`.
- All command output (core, whitelist, lang, history, ladder, strikes, mute, discord test) now uses the helpers.
- Tests: `MessageStyleTest` (upgrade, customised stays, placeholders kept, style rules: prefix, no shouting, max 20 words/sentence).
  `LegacyMigrationTest`: the old-code oracle still proves identical decisions, strikes, commands and actions; it now reads the same
  old config with old default message texts swapped for the new defaults (message texts follow the new defaults by design).
  `SettingsRegistryTest` checks that each changed old default is recorded in `OLD_DEFAULTS`.

## Not changed (left for later)
GUI titles/lore (`engine.gui`, asserted by many tests, plain labels), Discord webhook templates and Discord bot replies (plain text,
`discord.templates.*`), `mute.no-reason-text`, punishment-commands.yml example commands (section, not upgraded; "Bad language" example text only).

## Message style guide
Palette (Minecraft & codes): prefix `&8[&6&lAntiBadWords&8]&r `; gold `&6` brand/headings; aqua `&b` info/input prompts;
green `&a` success; yellow `&e` warning/notice; red `&c` only the key word of a block/penalty/error; gray `&7` text; white `&f` values;
`&l` bold for key words, `&o` italic for hints and the offending word. Every key word ends the colour by the next `&7` (codes reset formatting).
Rules: max 20 words per sentence, active voice, present tense, one instruction per sentence, no slang, no CAPS, no "please", keep all {placeholders}.

Before / after:
- warning-player: `&EWATCH YOUR WORDS. YOU ARE PUNISHED IF YOU SAY &F{REMAINING}&E BADWORDS AGAIN.`
  -> `&8[&6&lAntiBadWords&8]&r &e&lWarning. &7Your message contains a word that is not permitted. After &f{remaining}&7 more violations, a penalty is applied.`
- punishment-player: `&CFOR YOUR BADWORDS, YOU ARE PUNISHED WITH &F{PUNISHMENTS}&C.`
  -> `... &c&lPenalty. &7You used too many words that are not permitted. The penalty is: &f{punishments}&7.`
- mute blocked: `&cYou are muted. Time left: {remaining}. Reason: {reason}` -> `... &c&lMuted. &7You cannot write now. Time left: &f{remaining}&7. Reason: &f{reason}&7.`
- command: `§cNo permission.` -> `[AntiBadWords] Error. No permission.` (red bold key word, gray text)
