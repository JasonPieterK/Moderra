# AntiBadWords for Fabric and Forge

Server-side mods with the same matcher, word lists, protection levels and strike system as the Bukkit plugin.
Players do **not** need to install anything.

| Platform | Folder | Status |
|----------|--------|--------|
| Bukkit / Spigot | repo root (`pom.xml`) | built, tested, jars in `dist/` |
| Paper / Purpur | same jar as Bukkit | Purpur loads Paper plugins; not tested on a Purpur server |
| Fabric | `mods/fabric` | **source only - not compiled** |
| Forge | `mods/forge` | **source only - not compiled** |

## What was verified, and what was not

Verified (run `cd mods/core && mvn test`, 6 tests pass): the loader-independent core in `mods/core/src/main/java`
(config loading, word matching, protection levels, strike counting, custom words, colour codes).

**Not verified:** `mods/core/src/mc`, `mods/fabric` and `mods/forge`. The build sandbox could not reach Mojang, Fabric,
Forge, Paper or Purpur servers, so the Minecraft-facing code was written from memory of the Mojang-named API and has
never been compiled or run. Expect to fix a few compile errors on first build. Likely spots:

* `ModActions.isOperator` - the permission API changed in 1.21.11 (`hasPermission(int)`); fix in that one method.
* `ModActions.runCommand` - `Commands.performPrefixedCommand` signature.
* `ResourceLocation`/`Identifier`-style renames do not appear in this code (punishments run as vanilla commands
  on purpose, to keep the API surface small).
* `gradle.properties` in each loader folder contains `REPLACE_WITH_...` version numbers I could not look up, and
  the Forge `build.gradle` plugin id may differ for the newest Forge builds.

The target is Minecraft **26.3**, the newest release listed in this repo's `build-all.sh`. I could not check it against
Mojang's release list; change `minecraft_version` if a newer stable release exists.

## Building

```bash
cd mods/fabric && gradle build      # -> build/libs/AntiBadWords-fabric-1.10+26.3.jar   (needs JDK 25)
cd mods/forge  && gradle build      # -> build/libs/AntiBadWords-forge-1.10+26.3.jar
cd mods/core   && mvn test          # unit tests of the shared core (JDK 21+)
```

## Differences from the Bukkit plugin

* Config is `config/antibadwords.json` (generated on first start from `mods/core/src/main/resources/antibadwords-default.json`;
  regenerate that file from the plugin's `config.yml` with `python3 mods/tools/gen_default_config.py`).
* Scans **chat and commands that carry a chat message** (Fabric: `/say`, `/msg`, `/me`...; Forge: chat only).
  Signs, books and anvil renames are not scanned.
* Punishments are server commands (`effect give {player} minecraft:blindness 3 0 true`, `playsound ...`, `kick ...`).
  The plugin's attribute punishments are not available.
* Permissions: `/abw` needs operator level 2; staff alerts go to operators.
* No `listwords` subcommand; the others (`reload addword removeword check violations resetviolations info`) work the same.
