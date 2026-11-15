# Modrinth upload hand-off

For whoever (human or agent) publishes the project. Jason pastes the page text manually
([PAGE_TEXT.md](PAGE_TEXT.md)) and the gallery images ([SCREENSHOT_PLAN.md](SCREENSHOT_PLAN.md)); this file is
only about the project settings and the six version uploads.

## Before uploading - must be done first

- [ ] Test at least the newest jar (26.3) and the 1.21.11 jar on a real **Paper** server **and a real Spigot** server (BuildTools): start-up, a blocked word,
      a punishment with one effect and one attribute (check the attribute returns to normal), `/abw check`.
      The jars were compiled and unit-tested, **never run on a server**.
- [ ] Logo is ready (Jason is doing this later).
- [ ] Real screenshots uploaded.

## Project settings

| Field | Value |
|-------|-------|
| Name | AntiBadWords |
| Slug | `antibadwords` (if taken: `anti-bad-words`) |
| Project type | Plugin |
| Summary (max 256 chars) | Spigot/Paper profanity filter for chat, commands, signs, books and anvils in 7 languages, with configurable punishments. |
| Categories | Moderation, Utility (Modrinth category names may differ slightly: pick the closest) |
| Environment | Server side only; client: unsupported |
| Licence | MIT |
| Source | https://github.com/JasonPieterK/AntiBadWordsPlugin |
| Issues | https://github.com/JasonPieterK/AntiBadWordsPlugin/issues |
| Donation / Discord / Wiki | none yet - leave empty |

## Loaders

- **Paper** - yes.
- **Spigot** - yes (and **Bukkit**, if Modrinth requires it next to Spigot). The jars use only the Spigot API.
- **Purpur** - tick it only if Jason tested on Purpur; it is a Paper fork so it should work.
- **Folia** - **do not tick**.
- **Fabric / Forge / NeoForge** - **do not tick**: this is a plugin, not a mod.

## Versions to publish (one upload per jar)

Version number `1`, release channel **Release**, files from `1-download-jars/`. Do not upload the same jar to multiple
Minecraft versions it was not built for.

| Jar | Game version(s) to tick | Loader |
|-----|-------------------------|--------|
| `Antibadwords-RELEASE[Bukkit] - 2-26.3.jar` | 26.3 | Paper, Spigot, Bukkit |
| `Antibadwords-RELEASE[Bukkit] - 2-26.2.jar` | 26.2 | Paper, Spigot, Bukkit |
| `Antibadwords-RELEASE[Bukkit] - 2-26.1.2.jar` | 26.1.2 | Paper, Spigot, Bukkit |
| `Antibadwords-RELEASE[Bukkit] - 2-26.1.1.jar` | 26.1.1 | Paper, Spigot, Bukkit |
| `Antibadwords-RELEASE[Bukkit] - 2-26.1.jar` | 26.1 | Paper, Spigot, Bukkit |
| `Antibadwords-RELEASE[Bukkit] - 2-1.21.11.jar` | 1.21.11 | Paper, Spigot, Bukkit |
| `Antibadwords-RELEASE[Purpur] - 2-26.3.jar` | 26.3 | Purpur |
| `Antibadwords-RELEASE[Purpur] - 2-26.2.jar` | 26.2 | Purpur |
| `Antibadwords-RELEASE[Purpur] - 2-26.1.2.jar` | 26.1.2 | Purpur |
| `Antibadwords-RELEASE[Purpur] - 2-26.1.1.jar` | 26.1.1 | Purpur |
| `Antibadwords-RELEASE[Purpur] - 2-26.1.jar` | 26.1 | Purpur |
| `Antibadwords-RELEASE[Purpur] - 2-1.21.11.jar` | 1.21.11 | Purpur |
| `Antibadwords-RELEASE[Fabric] - 2-26.3.jar` | 26.3 | Fabric |
| `Antibadwords-RELEASE[Fabric] - 2-26.2.jar` | 26.2 | Fabric |
| `Antibadwords-RELEASE[Fabric] - 2-26.1.2.jar` | 26.1.2 | Fabric |
| `Antibadwords-RELEASE[Fabric] - 2-26.1.1.jar` | 26.1.1 | Fabric |
| `Antibadwords-RELEASE[Fabric] - 2-26.1.jar` | 26.1 | Fabric |
| `Antibadwords-RELEASE[Fabric] - 2-1.21.11.jar` | 1.21.11 | Fabric |
| `Antibadwords-RELEASE[Forge] - 2-26.3.jar` | 26.3 | Forge |
| `Antibadwords-RELEASE[Forge] - 2-26.2.jar` | 26.2 | Forge |
| `Antibadwords-RELEASE[Forge] - 2-26.1.2.jar` | 26.1.2 | Forge |
| `Antibadwords-RELEASE[Forge] - 2-26.1.1.jar` | 26.1.1 | Forge |
| `Antibadwords-RELEASE[Forge] - 2-26.1.jar` | 26.1 | Forge |
| `Antibadwords-RELEASE[Forge] - 2-1.21.11.jar` | 1.21.11 | Forge |

The version 2 jars are built by GitHub Actions (download them from the GitHub Release `v2.0.0`). They were NOT started on a real server yet; only the version 1 Fabric and Forge jars were. Say so in the upload notes or test them first.

Version name pattern: `AntiBadWords 2 for Minecraft 26.3`.

Changelog text for every upload:

```
Version 2
- Shared engine, config folder, moderation (strikes, ladder, mute, history), Discord, in-game menu, plugin integrations (see CHANGELOG)
- Version 1 notes: 23 bug fixes (inverted protection levels, swapped messages, unsafe attribute punishments, false positives, ...)
- Now works on both Spigot and Paper (one jar)
- New word matcher: whole-word, Unicode-aware, catches spaced letters, leetspeak and f*ck
- New permissions: antibadwords.bypass, antibadwords.notify
- New commands: /abw check, /abw info
- Config: per-source toggles, scanned command list, whitelist, violation expiry
Full list: https://github.com/JasonPieterK/AntiBadWordsPlugin/blob/main/CHANGELOG.md
```

Dependencies: none.

## Rules for the publisher

- Never claim a Minecraft version or loader that is not in the tables above.
- Do not publish if the pre-upload tests failed; report back instead.
- Rebuild jars with `2-plugin-bukkit-purpur/build-all-versions.sh` (needs JDK 21 and JDK 25); do not edit jars by hand.
