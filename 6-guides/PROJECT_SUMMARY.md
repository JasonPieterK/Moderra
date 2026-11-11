# Project summary

**AntiBadWords** is a Spigot/Paper server **plugin** (not a mod) that blocks profanity and punishes repeat offenders.
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
6. **Spigot + Paper support (v1.10)**: removed every Paper-only API (Adventure components, `AsyncChatEvent`,
   `getPluginMeta`, `getMinecraftVersion`); one jar runs on both.
7. **Multi-version builds**: one jar per Minecraft release of the last 12 months, built by `build-all-versions.sh`.
8. **Tests**: 22 unit tests (matcher, placeholders, config parsing of the shipped `config.yml`).
9. **Organised the repo**: `.gitignore`, build output removed from git, `1-download-jars/` for release jars,
   `6-guides/` for documentation, MIT `LICENSE`.

## Supported Minecraft versions

| Minecraft | Jars (`1-download-jars/`) | Java on the server |
|-----------|----------------|--------------------|
| 26.3 | `Antibadwords-RELEASE[Bukkit] - 1-26.3.jar`, `Antibadwords-RELEASE[Purpur] - 1-26.3.jar` | 25 |
| 26.2 | `Antibadwords-RELEASE[Bukkit] - 1-26.2.jar`, `Antibadwords-RELEASE[Purpur] - 1-26.2.jar` | 25 |
| 26.1 | `Antibadwords-RELEASE[Bukkit] - 1-26.1.jar`, `Antibadwords-RELEASE[Purpur] - 1-26.1.jar` | 25 |
| 1.21.11 | `Antibadwords-RELEASE[Bukkit] - 1-1.21.11.jar`, `Antibadwords-RELEASE[Purpur] - 1-1.21.11.jar` | 21+ |

Only full releases (no snapshots, pre-releases or betas). Version numbering was reset: every jar is release `1`
(the old `V1.10` jars and the 26.1.1 / 26.1.2 builds were dropped). Fabric and Forge: see `6-guides/FABRIC-AND-FORGE-STATUS.md`.
The Bukkit/Purpur jars were repackaged from the verified 1.10 builds (only `plugin.yml`'s version changed to `1`);
the next `2-plugin-bukkit-purpur/build-all-versions.sh` on a machine with the Spigot repository produces them from source.

## Server software

| Software | Status |
|----------|--------|
| Spigot | Supported: the jars are compiled against the Spigot API and use no Paper-only classes |
| Paper | Supported (Paper includes the whole Bukkit/Spigot API) |
| Purpur and other Paper/Spigot forks | Expected to work - **not tested** |
| CraftBukkit | Should work (Spigot API) - not tested |
| Folia | **Not supported**: uses the regular Bukkit scheduler |
| Fabric / Forge | **Not possible** with this code: it is a Bukkit-API plugin. A Fabric mod would be a separate port. |

## Honest status

- Compiles against the real Spigot API of all 6 versions (so it uses nothing Paper-only); 22 unit tests pass.
- **Never run on an actual server** (the sandbox this was built in could not start one). Test on a server
  before publishing: especially the punishment effects/attributes, signs, books and the anvil.

## Still open

- Logo (waiting for Jason).
- Real gallery screenshots (plan: [modrinth/SCREENSHOT_PLAN.md](modrinth/SCREENSHOT_PLAN.md)).
- A Fabric/Forge mod would be a separate project (this is a Bukkit-API plugin).
