# Changelog

## 1 (numbering restarted)

* Jar names are now `Antibadwords-RELEASE[LOADER] - VERSION-MCVERSION.jar`, release number reset to 1.
* Versions: 26.3, 26.2, 26.1, 1.21.11 (26.1.1 and 26.1.2 builds dropped).
* New Purpur download (same code as the Bukkit jar).
* Fabric and Forge mod sources added under `mods/` (same features and `config.yml`; not compiled yet).
* The code is otherwise the 1.10 code below.

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
- Jars for every Minecraft release of the last year: 1.21.11, 26.1, 26.1.1, 26.1.2, 26.2, 26.3 (`build-all.sh`).
- `antibadwords.bypass` and `antibadwords.notify` permissions.
- `/abw check <text>`, `/abw info`, tab completion for words and players.
- Per-source toggles (`filters.scan.*`), scanned/ignored command lists, whitelist.
- Detection of spaced letters (`f u c k`), masked words (`f*ck`), zero-width characters, Cyrillic look-alikes,
  multi-word entries (`fick dich`), and book titles.
- Violations can expire (`punishment.violations-expire-minutes`); sound punishment enabled by default.
- Unit tests for the matcher, placeholders and config parsing.
- Full-width Unicode letters (`ＳＨＩＴ`) are now normalised and caught.
