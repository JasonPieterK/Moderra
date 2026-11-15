# Fabric and Forge: what works, what was tested

## Version 2: what works on Fabric and Forge, what was tested

All 24 jars are built on GitHub (`.github/workflows/build-release.yml`) and the Fabric and Forge code compiles against the **real**
Minecraft, Fabric and Forge APIs (Minecraft 1.21.11, 26.1, 26.1.1, 26.1.2, 26.2, 26.3). The engine has 410 unit tests.
**Nothing of version 2 was run on a real server, and the scripted player was not used in this round.**

| Feature on Fabric / Forge | Engine tests | On a real server |
|---|:---:|:---:|
| Checking (chat, commands, signs both sides, books, anvils), strikes, messages, `/abw` | pass | not run |
| Config folder, migration, word lists, `/abw lang`, `/abw whitelist` | pass | not run |
| World profiles by permission, default and **world name** (dimension id, e.g. `minecraft:the_nether`) | pass | not run |
| Command blocks (running and saving a command) and sign back side | pass | not run |
| Player IP for `{ip}` (ban templates) | pass | not run |
| Spam, link and bypass-trick filters; `censor` becomes **block** (mods cannot change chat text) | pass | not run |
| Moderation: strikes, ladder, mute, history; kick through the vanilla `/kick` command | pass | not run |
| Discord **webhook** alerts | pass | not run |
| Settings menu (chest): names, glow, click blocking; **no lore**, chat prompts only | pass (menu logic) | not run |
| Permissions: Fabric asks the Fabric Permissions API (LuckPerms), Forge registers nodes; defaults as on Bukkit | - | not run |
| Network sync in shared-folder mode | pass | not run |

Not available on Fabric/Forge by design: DiscordSRV features, all plugin integrations (EssentialsX, PlaceholderAPI, Vault, ...), because
those are Bukkit-family plugins. LuckPerms for Fabric/Forge works through the permission nodes only.

### Open items for 2.0
The one complete list is in [`../handoffs/BUILD-VERIFICATION-TODO.md`](../handoffs/BUILD-VERIFICATION-TODO.md). Fabric and Forge parts:
* Test every jar on a real server (`TEST=1 ./build-everything.sh`, scripted player `bot.js`).
* Menu: item lore, sign-editor prompt (chat prompt only now).
* `hasPlugin` on mods (so `integrations.bans.provider: auto` and Bedrock `auto` always mean vanilla / off).
* Styled Chat and Text Placeholder API (eu.pb4) support.
* Forge: permission nodes of profiles added after server start need a restart.
* The 26.3 sign-back check relies on a class that was not in the API dump (`SignTextSlot.toString()` giving "BACK").

---

# Older notes (version 1 jars)

## The version-1 jars

The Fabric and Forge versions do exactly what the Bukkit plugin does. In version 1 they read the **same `config.yml`**
(copied to `config/antibadwords/config.yml` on first start), use the same `WordMatcher`, the same word lists, protection
levels, messages, strike system and punishments, and the same `/abw` command. Players install nothing.

Jar names (version 1): `Antibadwords-RELEASE[Fabric] - 1-26.3.jar` and `Antibadwords-RELEASE[Forge] - 1-26.3.jar`
(one per Minecraft version: 26.3, 26.2, 26.1.2, 26.1.1, 26.1, 1.21.11).

## What was tested, honestly

There are three levels of testing. Each one catches different bugs.

| Level | What it is | Bukkit / Purpur | Fabric | Forge |
|-------|-----------|:---------------:|:------:|:-----:|
| 1. Unit tests | automatic tests of the logic (`mvn test`) | 24 tests (version 1) | 17 tests (version 1) | 17 tests (version 1) |
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
  `5-shared-engine/src/mc-variants/` holds one tiny shim for each; the build picks one per version.
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
cd 5-shared-engine && mvn test            # unit tests of the shared engine (410 tests)
```
Needs Maven, Gradle 9.8+ (`GRADLE=/path/to/gradle`) and JDKs 8, 21 and 25. The scripted player needs Node.js:
`cd 5-shared-engine/live-player-test && npm install && node bot.js <host> <port> <minecraft version> extras`
(see the comments at the top of `bot.js`).

To add a Minecraft version, add one line to `VERSIONS` in `3-mod-fabric/build-all-versions.sh` and
`4-mod-forge/build-all-versions.sh`, and one to `MINECRAFT_VERSIONS` in `2-plugin-bukkit-purpur/build-all-versions.sh`.
