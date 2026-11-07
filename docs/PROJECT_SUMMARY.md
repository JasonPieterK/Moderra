# Project summary

**AntiBadWords** is a Paper server **plugin** (not a mod) that blocks profanity and punishes repeat offenders.
Current version: **1.10**. Licence: MIT. Author: Jason P.

## What was done (by Bram, with Jason)

1. **Reviewed the whole codebase** and found/fixed 23 bugs (details: [CHANGELOG](../CHANGELOG.md)).
   Biggest ones: swapped warning messages, inverted protection levels, Chinese/Cyrillic words that never
   matched, many false positives (`closer`, `scrap`, `peacock`), attribute punishments that could stay
   applied forever after quitting/dying, admins being blocked by their own filter, anvil spam.
2. **Rewrote word matching** (`WordMatcher`): whole-word, Unicode aware, leetspeak, spaced letters,
   masked words (`f*ck`), zero-width characters, Cyrillic look-alikes, multi-word entries, optional
   partial matching with a whitelist.
3. **Hardened punishments** (`PunishmentExecutor`, `PunishmentParser`): effects, temporary attribute
   changes (always restored: timer, quit, kick, death/respawn, plugin disable, crash repair on join),
   sounds, console commands, a final command.
4. **Fixed word-list translations** and added real Cyrillic spellings.
5. **Added features**: `antibadwords.bypass` / `antibadwords.notify`, `/abw check`, `/abw info`, per-source
   toggles, command scan list, whitelist, violation expiry, console logging.
6. **Multi-version builds**: one jar per Minecraft release of the last 12 months, built by `build-all.sh`.
7. **Tests**: 21 unit tests (matcher, placeholders, config parsing of the shipped `config.yml`).
8. **Organised the repo**: `.gitignore`, build output removed from git, `dist/` for release jars,
   `docs/` for documentation, MIT `LICENSE`.

## Supported Minecraft versions

| Minecraft | Jar | Java on the server |
|-----------|-----|--------------------|
| 1.21.11 | `dist/AntiBadWords.1.21.11.V1.10.jar` | 21+ |
| 26.1 | `dist/AntiBadWords.26.1.V1.10.jar` | 25 |
| 26.1.1 | `dist/AntiBadWords.26.1.1.V1.10.jar` | 25 |
| 26.1.2 | `dist/AntiBadWords.26.1.2.V1.10.jar` | 25 |
| 26.2 | `dist/AntiBadWords.26.2.V1.10.jar` | 25 |
| 26.3 | `dist/AntiBadWords.26.3.V1.10.jar` | 25 |

Only full releases from the last year (Nov 2025 - Nov 2026). Snapshots, pre-releases and betas are excluded.
Minecraft 1.21.10 (Oct 2025) falls just outside "the last year" and was not built.

## Server software

| Software | Status |
|----------|--------|
| Paper | Supported (what the jars are compiled against) |
| Purpur | Expected to work (Paper fork) - **not tested** |
| Spigot / CraftBukkit | **Not supported**: the plugin uses Paper-only APIs (`AsyncChatEvent`, Adventure components, `getPluginMeta`) |
| Folia | **Not supported**: uses the regular Bukkit scheduler |
| Fabric / Forge | **Not possible** with this code: it is a Bukkit-API plugin. A Fabric mod would be a separate port. |

## Honest status

- Compiles against the real Paper API of all 6 versions; 21 unit tests pass.
- **Never run on an actual server** (the sandbox this was built in could not start one). Test on a server
  before publishing: especially the punishment effects/attributes, signs, books and the anvil.

## Still open

- Logo (waiting for Jason).
- Real gallery screenshots (plan: [modrinth/SCREENSHOT_PLAN.md](modrinth/SCREENSHOT_PLAN.md)).
- Decide whether to build a Spigot-compatible or Fabric version.
