# Fabric and Forge: what works, what was tested

The Fabric and Forge versions do exactly what the Bukkit plugin does. They read the **same `config.yml`**
(copied to `config/antibadwords/config.yml` on first start), use the same `WordMatcher`, the same word lists, protection
levels, messages, strike system and punishments, and the same `/abw` command. Players install nothing.

Jar names: `Antibadwords-RELEASE[Fabric] - 1-26.3.jar` and `Antibadwords-RELEASE[Forge] - 1-26.3.jar`
(one per Minecraft version: 26.3, 26.2, 26.1.2, 26.1.1, 26.1, 1.21.11).

## What was tested, honestly

There are three levels of testing. Each one catches different bugs.

| Level | What it is | Bukkit / Purpur | Fabric | Forge |
|-------|-----------|:---------------:|:------:|:-----:|
| 1. Unit tests | automatic tests of the logic (`mvn test`) | 24 tests | 17 tests | 17 tests |
| 2. Real server check | start a real dedicated server with the jar, force-load the classes we patch, type `/abw` commands into the console (`test-on-real-server.sh`, 9 checks) | **not done** | all 6 versions pass 9/9 | all 6 versions pass 9/9 |
| 3. Scripted player | a bot joins a real server and swears in chat, `/msg`, a book, a sign and an anvil (`live-player-test/`, 15 checks) | **not done** | 26.1, 26.1.1, 26.1.2, 1.21.11 | 26.1, 26.1.1, 26.1.2, 1.21.11 |

Level 3 could **not** be run for **26.2 and 26.3**: the bot library (mineflayer) does not speak those Minecraft protocols
yet. For those two versions only levels 1 and 2 apply (the code is identical to the versions that pass level 3, except
the sign-packet shim, see below).

Not tested anywhere: the Bukkit/Spigot/Paper/Purpur jars on a real server (only the unit tests, which cannot start
Minecraft); LuckPerms or other permission mods; attribute punishments and the `final-command` on a live player
(both are off by default); a server restart in the middle of an attribute punishment (the recovery ledger has unit tests
only).

## What the real runs caught (that compiling and unit tests did not)

* The operator check used a pre-1.21.11 API - compile error.
* The anvil mixin tried to `@Shadow` fields that belong to the parent class - the server crashed at startup.
* The sign packet differs between Minecraft versions: 26.3 has a record with `lines()`, 26.2 and older have `getLines()`.
  `5-shared-mod-code/src/mc-variants/` holds one tiny shim for each; the build picks one per version.
* ForgeGradle needs a JDK 8 for one of its tools; Loom needs Gradle 9.8+ running on JDK 25 even when compiling for Java 21.

## Feature parity with the plugin

| Plugin feature | Fabric / Forge |
|----------------|----------------|
| Chat, commands (`scanned-commands` / `ignored-commands`, `*`, `namespace:cmd`), signs, books (pages + title), anvil renames (one violation per anvil session) | same, live-tested (see above) |
| `filters.*` (word lists, protection level, matching options, whitelist, scan switches) | same file, same code |
| Warning / punishment messages, placeholders, `&` colours, staff notifications, console log line | same |
| `violations-before-punish`, `violations-expire-minutes`, `clear-violations-on-quit`, `final-command` | same |
| Punishments: effects, attributes, sounds, console commands (+ legacy `format:` lines) | same (sound punishment live-tested) |
| Attribute safety: original value remembered, only the newest punishment restores, restored on quit / kick / server stop, and after a crash on next join | same; the crash ledger is `config/antibadwords/pending-attributes.txt` instead of the player's NBT |
| `/abw` `/antibadwords` `/antiwords`: reload, addword, removeword, listwords/list, check, violations, resetviolations, info; tab completion; "No permission." | same - one shared piece of logic, unit tested, and run on every server |
| Custom words written back into `config.yml` keeping its comments | same |
| Permissions `antibadwords.admin` / `.notify` / `.bypass` (op / op / nobody) | Fabric: optional [Fabric Permissions API](https://github.com/lucko/fabric-permissions-api) (LuckPerms etc.), otherwise the defaults. Forge: registered as Forge permission nodes. |

Differences you can see (none remove a feature):

* Attribute timers count real seconds; the plugin counts server ticks (identical unless the server lags).
* Effects and sounds are applied through the vanilla `/effect` and `/playsound` commands.
* No coloured ASCII-art banner at startup, only log lines.

## Building and testing

```bash
./build-everything.sh                     # all 24 jars -> 1-download-jars/
TEST=1 ./build-everything.sh 26.3         # one version, and start real servers to check the Fabric/Forge jars
cd 5-shared-mod-code && mvn test          # unit tests of the shared code
```
Needs Maven, Gradle 9.8+ (`GRADLE=/path/to/gradle`) and JDKs 8, 21 and 25. The scripted player needs Node.js:
`cd 5-shared-mod-code/live-player-test && npm install && node bot.js <host> <port> <minecraft version> extras`
(see the comments at the top of `bot.js`).

To add a Minecraft version, add one line to `VERSIONS` in `3-mod-fabric/build-all-versions.sh` and
`4-mod-forge/build-all-versions.sh`, and one to `MINECRAFT_VERSIONS` in `2-plugin-bukkit-purpur/build-all-versions.sh`.
