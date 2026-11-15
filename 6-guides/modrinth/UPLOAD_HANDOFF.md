# Modrinth upload hand-off

For whoever (human or agent) publishes the project. Jason pastes the page text manually
([PAGE_TEXT.md](PAGE_TEXT.md)) and the gallery images ([SCREENSHOT_PLAN.md](SCREENSHOT_PLAN.md)); this file is
only about the project settings and the six version uploads.

## Before uploading - must be done first

- [ ] Test at least the newest jar (26.3) and the 1.21.11 jar on a real **Paper** server **and a real Spigot** server (BuildTools): start-up, a blocked word,
      a punishment with one effect and one attribute (check the attribute returns to normal), `/moderra check`.
      The jars were compiled and unit-tested, **never run on a server**.
- [ ] Logo is ready (Jason is doing this later).
- [ ] Real screenshots uploaded.

## Project settings

| Field | Value |
|-------|-------|
| Name | Moderra |
| Slug | `moderra` (if taken: `moderra`) |
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

Version number `1.0`, release channel **Release**. Download the files from the GitHub Release (see `1-download-jars/README.md`). Do not upload the same jar to multiple
Minecraft versions it was not built for.

| Jar | Game version(s) to tick | Loader |
|-----|-------------------------|--------|
| `Moderra26.3-1.0-paper.jar` | 26.3 | Paper, Spigot, Bukkit |
| `Moderra26.2-1.0-paper.jar` | 26.2 | Paper, Spigot, Bukkit |
| `Moderra26.1.2-1.0-paper.jar` | 26.1.2 | Paper, Spigot, Bukkit |
| `Moderra26.1.1-1.0-paper.jar` | 26.1.1 | Paper, Spigot, Bukkit |
| `Moderra26.1-1.0-paper.jar` | 26.1 | Paper, Spigot, Bukkit |
| `Moderra1.21.11-1.0-paper.jar` | 1.21.11 | Paper, Spigot, Bukkit |
| `Moderra26.3-1.0-purpur.jar` | 26.3 | Purpur |
| `Moderra26.2-1.0-purpur.jar` | 26.2 | Purpur |
| `Moderra26.1.2-1.0-purpur.jar` | 26.1.2 | Purpur |
| `Moderra26.1.1-1.0-purpur.jar` | 26.1.1 | Purpur |
| `Moderra26.1-1.0-purpur.jar` | 26.1 | Purpur |
| `Moderra1.21.11-1.0-purpur.jar` | 1.21.11 | Purpur |
| `Moderra26.3-1.0-fabric.jar` | 26.3 | Fabric |
| `Moderra26.2-1.0-fabric.jar` | 26.2 | Fabric |
| `Moderra26.1.2-1.0-fabric.jar` | 26.1.2 | Fabric |
| `Moderra26.1.1-1.0-fabric.jar` | 26.1.1 | Fabric |
| `Moderra26.1-1.0-fabric.jar` | 26.1 | Fabric |
| `Moderra1.21.11-1.0-fabric.jar` | 1.21.11 | Fabric |
| `Moderra26.3-1.0-forge.jar` | 26.3 | Forge |
| `Moderra26.2-1.0-forge.jar` | 26.2 | Forge |
| `Moderra26.1.2-1.0-forge.jar` | 26.1.2 | Forge |
| `Moderra26.1.1-1.0-forge.jar` | 26.1.1 | Forge |
| `Moderra26.1-1.0-forge.jar` | 26.1 | Forge |
| `Moderra1.21.11-1.0-forge.jar` | 1.21.11 | Forge |

The jars are built by GitHub Actions (download them from the newest GitHub Release, for example `v1.0`). They were NOT started on a real server yet; only the old version 1 Fabric and Forge jars were (that was a different code base). Say so in the upload notes or test them first.

Version name pattern: `Moderra 1.0 for Minecraft 26.3`.

Changelog text for every upload:

```
Version 1.0 (first release as Moderra; the project was renamed)
- Renamed: command /moderra, permissions moderra.* (the old nodes still work for one major version), folder plugins/Moderra (the old folder is moved automatically)
- Shared engine, config folder, moderation (strikes, ladder, mute, history), Discord, in-game menu, plugin integrations (see CHANGELOG)
- Earlier fixes: 23 bug fixes (inverted protection levels, swapped messages, unsafe attribute punishments, false positives, ...)
- Now works on both Spigot and Paper (one jar)
- New word matcher: whole-word, Unicode-aware, catches spaced letters, leetspeak and f*ck
- New permissions: moderra.bypass, moderra.notify
- New commands: /moderra check, /moderra info
- Config: per-source toggles, scanned command list, whitelist, violation expiry
Full list: https://github.com/JasonPieterK/AntiBadWordsPlugin/blob/main/CHANGELOG.md
```

Dependencies: none.

## Rules for the publisher

- Never claim a Minecraft version or loader that is not in the tables above.
- Do not publish if the pre-upload tests failed; report back instead.
- Rebuild jars with `2-plugin-bukkit-purpur/build-all-versions.sh` (needs JDK 21 and JDK 25); do not edit jars by hand.
