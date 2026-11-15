# Changelog

## 2.0 (jar names `Antibadwords-RELEASE[X] - 2-<mc>.jar`)

**Honest status:** all 24 jars are built by GitHub Actions (`build-release.yml`) and published as a GitHub Release for the tag
`v2.0.0`. The plugin and mod code was compiled on GitHub against the real Spigot, Minecraft, Fabric and Forge APIs, and
third-party plugin names were checked against the real jars (`verify-apis.yml`: 64 names, 0 failures). The shared engine has
410 unit tests. **Nothing of version 2 was run on a real server and nothing was tested with the scripted player in this
round.** The open list is in `handoffs/BUILD-VERIFICATION-TODO.md`.

### Licence
* The licence changed from MIT to **All Rights Reserved** (`LICENSE`). Version 1 stays under the licence it was published with.

### Core
* **One shared engine for all four server types** (`5-shared-mod-code/` became `5-shared-engine/`). Plugin and mods are thin adapters.
* **Config folder instead of one big file**: `config.yml`, `wordlists/<code>.yml`, `moderation/*.yml`, `filters/*.yml`, `worlds.yml`,
  `discord.yml`, `integrations.yml` and a `data/` folder. Every option has a comment. `6-guides/CONFIGURATION.md` is generated from the code.
* **Automatic migration** of an old `config.yml` (backup `config.yml.bak-<date>`; a test proves the same decisions before and after).
* **37 language files**; new installs start with English plus the custom list. `/abw lang`, `/abw whitelist`.
* **Rule profiles** (`worlds.yml`) by permission, group, chat channel, world name or dimension. On Fabric and Forge the world name is the dimension id.
* Mistakes in the config never break the server: wrong values fall back to defaults and are reported with file and key.
* **Faster matching** (indexed): 99 of 100 messages under 0.3 ms with every filter on (engine speed test).
* **Word lists:** thin lists (Swahili, Filipino, Hebrew, Bengali) extended, many false positives fixed, a false-positive test over all 37 languages.
  The whitelist now also protects Chinese, Japanese and Thai text.

### Filters (spam, links and bypass tricks are off or only noting until switched on)
* Bypass tricks (invisible characters, look-alike letters, leetspeak), spam and flood control, link filter, check order setting.
* More scanned places: player names on join, back of signs, command blocks (now also on Fabric and Forge).
* `/abw check <text>` names the check that fired.

### Moderation
* Persistent weighted strikes, escalation ladder (`warn`, `mute`, `kick`, `ban`, `tempban`, `ipban`, `command`, effects ...), built-in mute, history diary
  (`6-guides/MODERATION.md`).
* Ban plugins: LiteBans, AdvancedBan, LibertyBans, EssentialsX or vanilla for `tempban` / `ipban`, with templates and `{ip}`
  (last IP stored in `data/players.json`; turn off with `known-players.store-ip`).
* Bedrock (Floodgate / Geyser) name handling.

### Discord (`6-guides/DISCORD.md`)
* Webhook alerts and a log channel (secrets by `${ENV:NAME}`, queue, rate limits, no pings by players), `/abw discord test|status`.
* DiscordSRV mode (Bukkit family only): alerts, filter for Discord-to-Minecraft messages (block, censor, log-only), `!abw` commands with allow-lists.

### In-game menu (`6-guides/GUI.md`)
* `/abw` or `/abw gui [page]`: every setting, language lists, word browser, ladder editor, mutes, profile and world-rule editors, search, chat prompts.
* Edit rights per category: `antibadwords.gui.edit.<category>` (default true).
* Fabric and Forge: names only (no lore yet), chat prompts only.

### Integrations (`6-guides/INTEGRATIONS.md`, Bukkit family)
EssentialsX and EssentialsX Chat, PlaceholderAPI (placeholders), LuckPerms, Vault, TAB, NametagEdit, ProtocolLib packet scan, CoreProtect, Plan,
chat-channel plugins (TownyChat event, others by detection), mcMMO / Towny / Factions / Citizens / DecentHolograms command scanning,
network sync between servers (shared folder, plugin messaging).

### Messages
* All built-in messages and command output use one calm style (`6-guides/MESSAGE-STYLE.md`).

### Behaviour changes when you upgrade
* **Message texts:** a message that is still exactly the old default is replaced by the new default text (one log line). A message you changed is never touched.
* **New installs only:** the default whitelist is bigger; new installs scan more commands (`team`, `title`, `tellraw`, `bossbar`, `scoreboard`, `rename`);
  `link` and `evasion` sources include `discord-in`. Migrated installs keep the version 1 lists (written into their config).
* **Stale `data/strikes.json`:** when strikes are not kept (clear on quit true, or saving off) the file is renamed to `strikes.json.discarded` at start and shutdown. Nothing is deleted.
* New permissions: `antibadwords.mute`, `.history`, `.discord`, `.gui` (op), `.bypass.mute` (nobody), `.gui.edit.<category>` (true). Table in `README.md`.
* The console and `/abw check` print a few more lines; `/abw` lists more sub-commands.
* **Licence:** All Rights Reserved (see above).

### Not done / known limits (full list: `handoffs/BUILD-VERIFICATION-TODO.md`)
* Real-server tests of everything above; the scripted player was not run.
* Fabric/Forge: item lore in the menu, sign-editor prompt, `hasPlugin` (so no plugin detection), Styled Chat and Text Placeholder API.
* PacketEvents listener, DiscordSRV slash commands, mcMMO party chat event, CarbonChat / ChatControl / FactionsUUID events; VentureChat and HuskChat cannot be hooked; TAB and NametagEdit have no events.
* "censor" changes text only in chat on Bukkit (and Discord with DiscordSRV); everywhere else it blocks.

### For developers
* Settings registry, check pipeline, event stream, replaceable strike counter, one-class sub-commands, comment-preserving `YamlEditor` (`5-shared-engine/README.md`).
* `tools/stub-compile.sh` (hand-written stubs, a quick sanity check only), `.github/workflows/build-release.yml`, `.github/workflows/verify-apis.yml`.
* The old plugin code is kept in the tests as an oracle for the migration.

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
