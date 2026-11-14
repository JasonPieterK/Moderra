# Changelog

## Unreleased (source code only - no new jars yet)

**Honest status:** everything below is in the source code and covered by the engine's 130 unit tests. The plugin and mod
code that connects the engine to a real server could not be compiled against Spigot, Minecraft, Fabric or Forge where it
was written, and has never run on a server. The jars in `1-download-jars/` are still version 1. To-do list:
`handoffs/BUILD-VERIFICATION-TODO.md`.

### What is new
* **One shared engine for all four server types.** `5-shared-mod-code/` became `5-shared-engine/`. It holds every rule
  (checking text, strikes, messages, commands, config). The Bukkit plugin, the Fabric mod and the Forge mod are now thin
  adapters: build a message, ask the engine, apply the answer.
* **Config folder instead of one big file**: `config.yml`, `wordlists/<code>.yml`, `moderation/ladder.yml`, `worlds.yml`
  and a `data/` folder. All files are generated with a comment above every option.
* **Automatic migration.** An old `config.yml` is converted on the first start. The old file is kept as
  `config.yml.bak-<date>`. Your old word lists become `wordlists/*.yml` with exactly your old words, the same languages
  stay on, and a test compares every decision before and after: nothing changes.
* **37 language files** from `7-wordlists/` are shipped in the jar. New installs start with English plus your custom
  list. `/abw lang list`, `/abw lang enable <code>`, `/abw lang disable <code>`.
* **`/abw whitelist add <word> | remove <word> | list [page]`**: words that are never blocked. Works at once.
* **Different rules per world or group** (`worlds.yml`): named profiles that change any "per world" setting, picked by
  world name (with `*`), dimension name or the permission `antibadwords.profile.<name>`.
  Limit: on Fabric and Forge the world name is not available yet, so only the permission and the default profile work there.
* **Mistakes in the config do not break the server.** A wrong value is replaced by the default (or the nearest allowed
  number) and reported with file and key; unknown keys are reported; a file that cannot be read keeps the settings from before.
* Programmatic edits (custom words, whitelist, languages) change only the lines they must: comments and order stay.
* `6-guides/CONFIGURATION.md` is now generated from the code, so it cannot drift from the real options.

### Moderation (session 4)
* **Persistent strikes** (`data/strikes.json`, decay, cap), **escalation ladder** (`ladder.steps`: warn, mute, kick, ban, command),
  **built-in mute** (`/abw mute|unmute|mutes`, blocks chat, chosen commands, signs, books) and **history**
  (`/abw history`, monthly JSONL, retention, privacy defaults). Guide: `6-guides/MODERATION.md`.
* New permissions: `antibadwords.mute`, `.history`, `.discord`, `.gui` (operators), `.bypass.mute` (nobody), declared in
  `plugin.yml` and the Forge permission nodes; Fabric passes them to the Permissions API.
* Bukkit kicks now always run on the main thread. `bot.js` has `moderation` and `moderation-again` modes (never run).
* Fixed a flaky test failure ("Failed to delete temp directory"): tests now shut their engines down; `Engine.shutdown()` is idempotent.

### New filters (second step of this round) - all off or silent until you switch them on
* **Bypass tricks** (`filters/evasion.yml`, `filters/confusables.yml`). Before a message is compared with the word lists
  it is cleaned up: invisible characters removed, full-width and other special letter forms folded, accents ignored,
  look-alike letters of other alphabets read as Latin (and the other way round), small capitals and leetspeak read as
  letters. Every step has its own switch; accents can be kept per writing system. The look-alike table comes from the
  Unicode confusables data and can be extended. Normal text gives the same result as before (tested per language family).
  The trick itself can also be noted, cleaned or blocked (`detect.action`, default `log-only`).
* **Spam and flood control** (`filters/spam.yml`, default off): too many messages, repeated or nearly identical
  messages, capital letters, character and symbol floods, repeated words, too many commands. Each rule: on/off, limits,
  action (block / censor / log-only), message, strike weight (default 0 = never punishes), sources.
  Permission `antibadwords.bypass.spam`.
* **Link filter** (`filters/links.yml`, default off): URLs, bare domains, IPv4/IPv6, invite links and hidden forms
  (`example dot com`, `example(dot)com`, `example[.]com`, `example .com`, Unicode dots). Modes `block-all`,
  `allow-list`, `block-list`; own switches for IP addresses and invites. Permission `antibadwords.bypass.links`.
* **More places are scanned**: player names on join (`log`, `warn` or `kick`; off by default), the back side of signs
  (own switch on Bukkit), command blocks (Bukkit only, off by default), and new installs scan more commands
  (`team`, `title`, `tellraw`, `bossbar`, `scoreboard`, `rename`). An updated server keeps its old command list.
* **Check order** is a setting (`filters.check-order`, default `evasion, badword, link, spam`).
* **`/abw check <text>`** now names the check that fired and why; `/abw info` lists the filters and the check order.
* **Faster with big word lists**: masked (`f*ck`) and partial matching use an index instead of scanning every word.
  Measured with 10 000 words and 256-character messages (4-core test machine, JDK 21): 99 of 100 messages take under
  0.1 ms in the word matcher (the old code: about 3 ms with partial-match on) and under 0.3 ms with every filter on.
  The budget (under 1 ms) is a unit test.
* Limits: "censor" changes the text only in chat on Bukkit/Spigot/Paper/Purpur and blocks everywhere else. On Fabric
  and Forge command blocks are not scanned and the sign-back switch has no effect (both sides are always scanned).
  None of this has run on a real server yet.

### For developers
* Settings registry (`SettingsRegistry`, `Setting`), pipeline of checks (`TextCheck`, `Hit`, `Verdict`), event stream
  (`ModerationEvent`), replaceable strike counter (`StrikeService`), one-class sub-commands (`SubCommand`),
  comment-preserving `YamlEditor`. See `5-shared-engine/README.md`.
* The plugin's unit tests moved into the engine; the old code is kept in the tests as an oracle for the migration.
* New/extended test scripts that have **not been run yet**: `2-plugin-bukkit-purpur/test-on-real-server.sh`, more checks
  in the Fabric/Forge `test-on-real-server.sh` (`OLD_CONFIG=1` tests the migration), more checks in the scripted player.

### Things that behave differently on purpose
* New installs scan six more commands by default; migrated installs keep the old list (it is written into their config).
* The console gets a line for hits of the new checks (for example `Bob triggered evasion (invisible characters) in chat:
  noted only`) when `logging.console` is on. The `evasion` check is the only new check that is active by default.
* `/abw check` prints a second line naming the check; `/abw info` prints two more lines.
* Long comments in the generated config files wrap a little differently.
* `/abw` with no arguments lists two more sub-commands (`lang`, `whitelist`).
* `/abw reload` also prints config problems, if there are any.
* `/abw listwords <language>` uses the new codes (`en`, `de` ...); the old names (`english`, `german` ...) still work.
* Fabric/Forge only: a setting that is missing from the file now uses its default (like the plugin always did);
  the version-1 mods treated missing lists as empty.

## 1 (numbering restarted)

### What is new
* **Jar names** are now `Antibadwords-RELEASE[LOADER] - VERSION-MCVERSION.jar`, and the release number starts again at 1.
* **Four server types, six Minecraft versions = 24 jars**: Bukkit/Spigot/Paper, Purpur, Fabric and Forge for
  26.3, 26.2, 26.1.2, 26.1.1, 26.1 and 1.21.11 (the six newest full releases, checked against Mojang on 2026-11-08;
  Minecraft "26.0" does not exist).
* **Fabric and Forge mods** with the same features and the same `config.yml` as the plugin (chat, commands, signs,
  books, anvils, strikes, punishments, `/abw`). Every Fabric and Forge jar was started on a real server and checked.
* **Folder layout** rebuilt so it can be navigated at a glance (`1-download-jars/`, `2-plugin-...`, `3-mod-fabric/`, ...).
* `build-everything.sh`: one command builds all 24 jars.

### Bugs fixed
1. **Punishment text said "no sound" instead of "villager no sound".** Only the last word of the sound name was kept.
2. **Partial matching could be dodged with stretched letters.** With `partial-match: true`, `xxfuuuuuuckxx` or two
   stretched words glued together were not caught (while `xxfuckxx` was). The collapsed variants are now searched too.
3. **Out-of-range attribute punishment left a stale recovery marker** in the player's data. The marker is now removed when the value is rejected.
4. **Mod: operator check used an API that no longer exists** (Minecraft 1.21.11+ permission system) - found by compiling.
5. **Mod: anvil scanning crashed at startup** (Mixin cannot shadow a parent class's fields) - found by starting a
   real server. Fixed with an accessor mixin.
6. **Mod: sign packet API differs between Minecraft versions** (`getLines()` array up to 26.2, `lines()` list in 26.3).
   A tiny per-version shim picks the right one.
7. Build scripts: logs were deleted by `gradle clean`; Gradle had to run on JDK 25 even when compiling for Java 21.

### Tests added
* Plugin: 24 unit tests (was 22). Shared mod code: 17 unit tests.
* `test-on-real-server.sh` (Fabric and Forge): starts a real server and checks that the mixins apply and `/abw` works.
* `5-shared-engine/live-player-test/` (then called `5-shared-mod-code/`): a scripted player that joins a server, swears in chat, `/msg`, a book, a sign
  and an anvil, and checks the server's reaction.

## 1.10

### Bugs fixed

1. **Warning messages were swapped.** `warning-player` was broadcast to everybody while the personal message
   got the broadcast text (constructor arguments were passed in the wrong order).
2. **`{PUNISHMENTS}` was shown literally.** Only lower-case `{punishments}` was replaced, but the default config
   uses upper-case. Placeholders are now case-insensitive (`Placeholders`).
3. **Protection level was inverted.** LOW (1) blocked only the *mild* words and let `fuck` through; HIGH was the
   only level that worked. Now LOW blocks level-3 only, MEDIUM levels 2-3, HIGH everything.
4. **Leetspeak symbols never worked.** `@ $ ! |` were turned into spaces *before* being mapped to letters, so
   `sh!t`, `$hit`, `a$$` were never recognised.
5. **Chinese, Russian (Cyrillic) and accented words never matched.** All characters outside `a-z0-9` were
   stripped, so every hanzi entry became an empty word and `cuzão` became `cuzo`. Custom words with non-ASCII
   letters were also rejected. Matching is now fully Unicode aware.
6. **Many false positives.** All words were glued together and searched with `contains`, so `closer` (loser),
   `scrap` (crap), `coarse` (arse), `peacock` (cock), `computador` (puta)... were blocked. Matching is now
   whole-word; substring matching is opt-in (`partial-match`) and has a whitelist.
7. **Anvil rename spam.** `PrepareAnvilEvent` fires on every keystroke, so one rename gave many violations and
   many broadcasts. Now one violation per anvil session.
8. **Attribute punishments could permanently corrupt a player.** The original value was lost if the player quit
   before the timer ended, and two stacked punishments stored the *punished* value as the "original". Originals
   are now remembered (also in the player's persistent data), restored on timer end / quit / kick / disable and
   repaired on the next join after a crash.
9. **Admins could not manage the word list.** Every command was scanned, including `/abw addword <word>` itself.
   Only configured commands are scanned now (`filters.scanned-commands`) and the plugin's own are ignored.
10. **Duplicate messages on punishment** (warning + punishment broadcast, and "punished if you say 0 more").
11. **Staff notification only after punishment** and it required the admin permission. It is now sent for every
    blocked word, uses `antibadwords.notify`, and is logged to the console.
12. **Violation counter raced on the chat thread** (counted async, acted on the main thread). Everything is
    counted on the main thread now.
13. **Mislabeled / wrong attribute examples in config.yml** (`short_reach` edited armor, `reduce_jump_strength`
    edited attack speed, `reduce_attack` default equalled the normal value, ...). Renamed and corrected, and real
    reach/jump examples added.
14. **Removed-API risk on new versions.** `Attribute.values()`, `PotionEffectType.values()` and
    `Sound.valueOf()` are deprecated since 1.21.3; replaced with Registry lookups (works on 1.21.11 and 26.x).
    Old names such as `minecraft:generic.movement_speed`, `SLOW`, `ENTITY_VILLAGER_NO` are still accepted.
15. **Negative attribute values could not be used** in the legacy `format:` option (split on `-`).
16. **Slow matching.** Every chat message ran a regex for every blocked word; the word index is now built once.
17. **Config gaps.** `punishment.sounds` and `punishment.commands` were read but missing from config.yml.
18. **Word-list translation errors:** `svin'ya` (apostrophe split the word), `kozol` -> `kozel`, `gou` (too
    broad, now `gou dong xi`); real Cyrillic spellings added to the Russian list.
19. **Packaging:** build output (`target/`) was committed; `plugin.yml` version was hard-coded separately from
    the pom; `addword`/`removeword` only used the first argument (phrases were impossible).

### Follow-up fixes (punishments)

20. **Attribute punishments were not restored after the player died/respawned** (the timer held a stale
    `Player` object). The player is now looked up again when the timer fires.
21. **Negative values in the legacy `format:` option** (`name--2.5-10`) were mis-parsed; now handled.
22. Punishment config parsing moved into `PunishmentParser` and covered by tests that load the shipped config.yml.
23. `/abw listwords` now requires `antibadwords.admin` like every other sub-command.

### New

- **Works on Spigot and Paper** with the same jar: all Paper-only APIs were removed and the jars are
  compiled against the Spigot API.
- Jars for every Minecraft release of the last year: 1.21.11, 26.1, 26.1.1, 26.1.2, 26.2, 26.3 (`build-all-versions.sh`).
- `antibadwords.bypass` and `antibadwords.notify` permissions.
- `/abw check <text>`, `/abw info`, tab completion for words and players.
- Per-source toggles (`filters.scan.*`), scanned/ignored command lists, whitelist.
- Detection of spaced letters (`f u c k`), masked words (`f*ck`), zero-width characters, Cyrillic look-alikes,
  multi-word entries (`fick dich`), and book titles.
- Violations can expire (`punishment.violations-expire-minutes`); sound punishment enabled by default.
- Unit tests for the matcher, placeholders and config parsing.
- Full-width Unicode letters (`ＳＨＩＴ`) are now normalised and caught.
