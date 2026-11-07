# Modrinth upload hand-off

For whoever (human or agent) publishes the project. Jason pastes the page text manually
([PAGE_TEXT.md](PAGE_TEXT.md)) and the gallery images ([SCREENSHOT_PLAN.md](SCREENSHOT_PLAN.md)); this file is
only about the project settings and the six version uploads.

## Before uploading - must be done first

- [ ] Test at least the newest jar (26.3) and the 1.21.11 jar on a real Paper server: start-up, a blocked word,
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
| Summary (max 256 chars) | Block profanity in chat, commands, signs, books and anvils in 7 languages, and punish repeat offenders with effects, sounds or commands. |
| Categories | Moderation, Utility (Modrinth category names may differ slightly: pick the closest) |
| Environment | Server side only; client: unsupported |
| Licence | MIT |
| Source | https://github.com/JasonPieterK/AntiBadWordsPlugin |
| Issues | https://github.com/JasonPieterK/AntiBadWordsPlugin/issues |
| Donation / Discord / Wiki | none yet - leave empty |

## Loaders

- **Paper** - yes.
- **Purpur** - tick it only if Jason tested on Purpur; it is a Paper fork so it should work.
- **Spigot / Bukkit** - **do not tick**: the plugin needs Paper-only APIs and fails on plain Spigot.
- **Folia** - **do not tick**.
- **Fabric / Forge / NeoForge** - **do not tick**: this is a plugin, not a mod.

## Versions to publish (one upload per jar)

Version number `1.10`, release channel **Release**, files from `dist/`. Do not upload the same jar to multiple
Minecraft versions it was not built for.

| Jar | Game version(s) to tick | Loader |
|-----|-------------------------|--------|
| `AntiBadWords.1.21.11.V1.10.jar` | 1.21.11 | Paper |
| `AntiBadWords.26.1.V1.10.jar` | 26.1 | Paper |
| `AntiBadWords.26.1.1.V1.10.jar` | 26.1.1 | Paper |
| `AntiBadWords.26.1.2.V1.10.jar` | 26.1.2 | Paper |
| `AntiBadWords.26.2.V1.10.jar` | 26.2 | Paper |
| `AntiBadWords.26.3.V1.10.jar` | 26.3 | Paper |

Version name pattern: `AntiBadWords 1.10 for Minecraft 26.3`.

Changelog text for every upload:

```
Version 1.10
- 23 bug fixes (inverted protection levels, swapped messages, unsafe attribute punishments, false positives, ...)
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
- Rebuild jars with `./build-all.sh` (needs JDK 21 and JDK 25); do not edit jars by hand.
