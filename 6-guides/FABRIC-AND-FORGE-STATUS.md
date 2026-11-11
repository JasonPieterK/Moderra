# AntiBadWords for Fabric and Forge

Server-side mods that do exactly what the Bukkit plugin does. Players do **not** need to install anything.
They read the **same `config.yml`** as the plugin (it is copied into `config/antibadwords/config.yml` on first start),
use the same matcher (`WordMatcher`), word lists, protection levels, messages, strike system and punishments.

Jar names: `Antibadwords-RELEASE[Fabric] - 1-26.3.jar` and `Antibadwords-RELEASE[Forge] - 1-26.3.jar`.

## Status - read this first

| Part | Status |
|------|--------|
| `core/src/main` (config, matching, strikes, messages, `/abw` logic, attribute ledger) | **Compiled and tested**: `cd 5-shared-mod-code && mvn test` runs 17 tests against the plugin's real `config.yml` |
| `core/src/mc` (chat/command/sign/book/anvil scanning, punishments, `/abw` registration, mixins) | **Written, never compiled or run** |
| `fabric/`, `forge/` (entry points, permissions, Gradle builds) | **Written, never compiled or run** |

The build sandbox could not reach Mojang, Fabric, Forge, Paper or Purpur servers, so no Minecraft jar was available
to compile against. A syntax-only check of the unverified files found no syntax errors, but expect a few compile
errors on the first real build. **No Fabric or Forge jar has been produced.** Do not publish them before testing.

Where it will most likely need a fix:

* `ModPermissions.isOperator` - the permission API changed in 1.21.11 (`hasPermission(int)`); one method to fix.
* Mixin targets (names copied from 1.21.x, unverified for 26.x): `Commands.performCommand`,
  `ServerGamePacketListenerImpl.updateSignText` / `handleEditBook`, `AnvilMenu.createResult`,
  `ItemCombinerMenu.removed`. If a name moved, Mixin fails at startup with a clear message naming it.
* `gradle.properties` in each loader folder has `REPLACE_WITH_...` / guessed version numbers I could not look up,
  and the Forge `build.gradle` plugin id may differ for the newest Forge builds.
* `ModRuntime` uses `BuiltInRegistries.ATTRIBUTE`, `BuiltInRegistries.SOUND_EVENT`, `CommandSourceStack.withSuppressedOutput`,
  `Commands.performPrefixedCommand` - all long-standing, but unchecked against 26.x.

The target is Minecraft **26.3**, the newest release in this repo's `build-all-versions.sh`; it was not checked against
Mojang's release list. Change `minecraft_version` in `gradle.properties` for another release.

## Feature parity with the plugin

| Plugin feature | Fabric / Forge |
|----------------|----------------|
| Chat, commands (`scanned-commands` / `ignored-commands`, `*`, `namespace:cmd`), signs, books (pages + title), anvil renames (one violation per anvil session) | same |
| `filters.*` (word lists, protection level, matching options, whitelist, scan switches) | same file, same code |
| Warning / punishment messages, `{player} {word} {source} {count} {limit} {remaining} {punishments}`, `&` colours, staff notifications, console log line | same |
| `violations-before-punish`, `violations-expire-minutes`, `clear-violations-on-quit`, `final-command` | same |
| Punishments: effects, attributes, sounds, console commands (+ legacy `format:` lines) | same |
| Attribute safety: original value remembered, only the newest punishment restores, restored on quit / kick / server stop, and after a crash on next join | same (the crash ledger is `config/antibadwords/pending-attributes.txt` instead of the player's NBT) |
| `/abw` `/antibadwords` `/antiwords`: reload, addword, removeword, listwords/list, check, violations, resetviolations, info; tab completion; "No permission." messages | same - the command text is handled by the same logic, tested |
| Custom words written back into `config.yml` keeping its comments | same |
| Permissions `antibadwords.admin` / `.notify` / `.bypass` with the plugin's defaults (op / op / nobody) | Fabric: via the optional [Fabric Permissions API](https://github.com/lucko/fabric-permissions-api) (LuckPerms etc.), otherwise the defaults. Forge: registered as Forge permission nodes. |

Differences you can see (none remove a feature):

* Attribute timers count real seconds, the plugin counts server ticks (identical unless the server lags).
* Effects and sounds are applied through the vanilla `/effect` and `/playsound` commands.
* No coloured ASCII-art banner at startup, only log lines.
* Faithfully kept plugin quirk: the "punished with ..." text for a sound shows only the last name segment
  (`entity.villager.no` -> "no sound"). The plugin's own comment says it should be "villager no sound"; if you fix
  that, fix it in `PunishmentExecutor` and `Names.soundDescription` together.

## Building

```bash
cd 5-shared-mod-code   && mvn test          # unit tests of the shared core (JDK 21+), works offline from Maven Central
cd 3-mod-fabric && gradle build      # -> build/libs/Antibadwords-RELEASE[Fabric] - 1-26.3.jar   (needs JDK 25)
cd 4-mod-forge  && gradle build      # -> build/libs/Antibadwords-RELEASE[Forge] - 1-26.3.jar
```
