# AntiBadWords: shared context for the next feature round

Read this file completely before starting any session prompt. Every session prompt (`session-1-easy.md` ... `session-5-hard.md`)
assumes it. If this file is missing in your checkout, run `git pull` on `main` first.

## 1. What the product is

AntiBadWords blocks profanity in Minecraft chat, commands, signs, books and anvil renames and punishes repeat offenders.
It ships as **24 jars** = 4 server types x 6 Minecraft versions:

| Server type | Folder | Notes |
|---|---|---|
| Bukkit / Spigot / Paper (plugin) | `2-plugin-bukkit-purpur/` | Maven. One jar per Minecraft version. |
| Purpur | same code as above | Purpur runs Paper plugins; the jar is a renamed copy. |
| Fabric (server-side mod) | `3-mod-fabric/` | Gradle + Loom. Mojang names, Mixins. |
| Forge (server-side mod) | `4-mod-forge/` | Gradle + ForgeGradle 7. Mojang names, Mixins. |

Minecraft versions (the 6 newest full releases, verified against Mojang on 2026-11-08):
**26.3, 26.2, 26.1.2, 26.1.1, 26.1, 1.21.11.** 26.x needs Java 25; 1.21.11 needs Java 21. There is no Minecraft 26.0.
Jar naming (do not change): `Antibadwords-RELEASE[LOADER] - 1-<MCVERSION>.jar`, LOADER = Bukkit, Purpur, Fabric or Forge.

Repo map (numbered, plain-language folders; every folder has a README):
`1-download-jars/` finished jars | `2-plugin-bukkit-purpur/` plugin (`src/main/java/com/jason/plugins/antibadwords/`, `src/test/`,
`build-all-versions.sh`) | `3-mod-fabric/` | `4-mod-forge/` | `5-shared-mod-code/` (pure-Java `src/main/java/.../mod/`, Minecraft layer
`src/mc/java/`, per-version shims `src/mc-variants/`, tests, `live-player-test/` bot) | `6-guides/` docs | `build-everything.sh`.
The plugin's pure classes (`WordMatcher`, `Placeholders`, `PunishmentAction`, `AntiBadWordsSettings`) are compiled into the mods
as a source root. Fabric/Forge also compile `5-shared-mod-code/src/{main,mc}/java` plus `src/mc-variants/sign-<array|record>`.

## 2. Hard rules for every session

1. **Everything must be configurable by server admins.** No behaviour, number, message, list, threshold, duration, toggle or
   channel may be hard-coded. If you add a behaviour, add its settings (with sane defaults, comments, min/max) in the same change.
2. **All four server types stay in parity.** A feature that exists in the plugin must exist in Fabric and Forge too, unless this
   file or your prompt explicitly says it cannot (Discord bridge, see section 6). The same `config` folder format is used by all four.
3. **Behaviour-preserving by default.** An existing install with the old single `config.yml` must keep behaving the same after upgrade
   (automatic, non-destructive migration; the old file is backed up, never deleted).
4. **Never claim something works unless you ran it.** Report what you tested and what you could not. The honest test matrix lives in
   `README.md` and `6-guides/FABRIC-AND-FORGE-STATUS.md`; update it for what you add.
5. **Do not break the 24-jar build** or the jar names. Do not commit build output (`build/`, `target/`, `run/`, `node_modules/`).
6. Docs are ELI5: plain words, short sentences, a README in any folder you add, update `CHANGELOG.md` and `6-guides/CONFIGURATION.md`.
7. Never put secrets (webhook URLs, bot tokens) in code, tests, logs, chat messages, history files or the GUI; mask them everywhere.
8. Offensive-word lists are moderation data; handle them as data. Do not repeat slurs in commit messages, PR text or logs beyond what a
   file diff needs.

## 3. Build, test and environment (learned the hard way)

* Unit tests: `cd 5-shared-mod-code && mvn test` and `cd 2-plugin-bukkit-purpur && mvn clean test -Dmc.version=26.3 -Dmc.api.version=26.3 -Dspigot.api.version=26.3-R0.1-SNAPSHOT -Djava.release=25`.
* All jars: `./build-everything.sh [mc versions...]`; add `TEST=1` to also start real Fabric/Forge servers with each jar and check them
  (`3-mod-fabric/test-on-real-server.sh`, `4-mod-forge/test-on-real-server.sh`). A full `TEST=1` run takes about one hour. During development
  use a subset such as `26.3 26.1 1.21.11`; run the full set once before opening the final PR.
* Scripted player: `5-shared-mod-code/live-player-test/bot.js` joins a real server and checks chat, `/msg`, book, sign, anvil (supports
  26.1, 26.1.1, 26.1.2, 1.21.11; not 26.2/26.3 yet). Extend it for every new feature that a player can trigger.
* Toolchain: Maven 3.9, **Gradle 9.8+** (Loom 1.19 needs it, running on JDK 25), **JDK 25, 21 and 8** (ForgeGradle needs an old JDK 8 for one
  tool). Set `GRADLE=/path/to/gradle`, `JAVA8_HOME`, `JAVA21_HOME`, `JAVA25_HOME` if they are not under `/usr/lib/jvm`.
* If Maven Central answers HTTP 429, retry, or use `https://repo1.maven.org/maven2/`; Maven needs the proxy in `~/.m2/settings.xml`
  (it ignores JVM proxy properties). Gradle caches what it downloaded, so retries make progress.
* Mixin pitfalls: a Mixin cannot `@Shadow` a field that belongs to the target's parent class (use an `@Accessor` mixin on the parent, see
  `ItemCombinerMenuAccessor`); Minecraft APIs differ between versions (see the `SignLines` shims in `5-shared-mod-code/src/mc-variants/`).
  Fabric and Forge APIs changed a lot in 26.x (Forge: events have a static `BUS`, a `Predicate` listener returns true to cancel; the mod
  constructor takes `FMLJavaModLoadingContext`). Read the real API with `javap` from the Gradle cache, do not guess.
* Real-server test of the Bukkit/Purpur jars has never been done. Try to get a Paper or Purpur server jar for 26.x and add
  `2-plugin-bukkit-purpur/test-on-real-server.sh` plus a bot run; if you cannot download a server, say so in your report.

## 4. Architecture contracts (the sessions build on these names)

Session 2 creates the foundation; sessions 3-5 build on it. Keep these names so parallel work merges cleanly.

* **Engine**: a platform-neutral Java library, folder `5-shared-engine/` (session 2 renames `5-shared-mod-code/` to this with `git mv` and
  fixes every script/doc/Gradle/Maven reference). It has **no** Bukkit/Minecraft imports. Package `com.jason.plugins.antibadwords.engine.*`.
  Moved-in existing pure classes may keep their current package. It is compiled into every loader's jar as a source root (no separate jar).
  Allowed third-party libs: SnakeYAML and Gson only (both exist on every server).
* `Platform` (interface the loaders implement): online players, send/broadcast message, run console command, kick, permission check
  (`has(uuid, node)`), world name of a player, schedule sync/async task, data folder, logger, server/platform name.
* `Engine`: `Engine.load(Platform, Path configDir)`, `reload()`, `Verdict inspect(Message)`, `onViolation(...)`, `events()`, `settings()`, `commands()`.
  `Message` = player uuid/name, world, source (`chat|command|sign|book|anvil|join|...`), text, permission lookup. `Verdict` = `ALLOW`, `BLOCK`
  or `CENSOR(text)` plus reasons, so every listener/mixin shrinks to "build a Message, call inspect, apply the Verdict".
* `TextCheck` (pipeline stage): `Optional<Hit> check(Message, EffectiveSettings)`; `Hit` = check id (`badword|spam|caps|link|evasion|...`), detail,
  severity, suggested action (`BLOCK|CENSOR|LOG_ONLY`), strike weight. Checks are registered with `engine.registerCheck(order, check)`;
  order and enabled-state come from config.
* `SettingsRegistry` + `Setting<T>`: the single source of truth for configuration. Each setting has: key path (for example
  `filters.spam.max-messages-per-window`), type (BOOLEAN, INT, DOUBLE, STRING, ENUM, DURATION, STRING_LIST, SECRET), default, min/max/allowed values,
  category (becomes a page in the GUI), title, description, owning file. Config loading, default-file generation (with comments),
  `6-guides/CONFIGURATION.md` generation, validation messages and the in-game GUI are all driven from it. Every feature registers its settings via a
  `static void register(SettingsRegistry r)` method.
* `ModerationEvent` (generic so sessions do not collide): `kind` (string), `time`, player uuid/name, world, source, `Map<String,String> data`.
  Kinds include `violation`, `punishment`, `mute`, `unmute`, `mute-expired`, `spam`, `link`, `evasion`, `admin-command`, `discord-in`.
  Subscribers: `engine.events().subscribe(Consumer<ModerationEvent>)`. History (session 4) and Discord (session 5) are subscribers.
* `StrikeService` interface: session 2 provides the current in-memory behaviour behind it; session 4 swaps in the persistent, weighted, decaying one.
* **Config folder** (same layout on every loader; plugin: `plugins/AntiBadWords/`, mods: `config/antibadwords/`):
  ```
  config.yml                 general: language list on/off, protection level, scan switches, messages, logging   (config-version: 2)
  wordlists/<code>.yml       one file per language (format in 7-wordlists/README.md), plus custom.yml for the admin's own words
  filters/spam.yml  filters/evasion.yml  filters/links.yml
  moderation/ladder.yml  moderation/mute.yml  moderation/history.yml
  discord.yml  worlds.yml
  data/                      state, never hand-edited: strikes.json, mutes.json, players.json, history/*.jsonl, gui sessions
  ```
  Old single-file `config.yml` installs are migrated automatically on first start (backup `config.yml.bak-<date>`); old language names map
  `english->en, uk_english->en-gb, indonesian->id, portuguese_brazil->pt-br, russian->ru, german->de, chinese->zh, custom->custom`.
  Comments in admin-edited YAML must survive programmatic edits (custom words, whitelist, GUI saves).
* **Word lists** live at the repo root in `7-wordlists/` (data source of truth, created by session 1) and are copied into every jar's resources
  and into the config folder on first start. Tiers: level-1 mild, level-2 rude, level-3 severe; protection level 1 keeps only tier 3,
  2 keeps tiers 2-3, 3 keeps all (this is already how the code works and must not change).
* **Permissions** (Bukkit nodes; Fabric via the optional Permissions API; Forge as registered permission nodes; defaults in brackets):
  existing `antibadwords.admin` [op], `.notify` [op], `.bypass` [nobody]. New: `antibadwords.gui` [op], `.mute` [op], `.history` [op],
  `.bypass.spam` / `.bypass.links` / `.bypass.evasion` [nobody], `.discord` [op], `.profile.<name>` [nobody].

## 5. Feature to session map

| # | Feature | Session |
|---|---|---|
| word lists, 35 languages | 37 files incl. Indonesian, English, Chinese, Russian, Korean, Japanese, Malay ... | 1 |
| foundation | engine extraction, folder config, settings registry, migration, events, `/abw lang` | 2 |
| 10 | per-world / per-group rule profiles (`worlds.yml`) | 2 |
| 12 | `/abw whitelist add|remove|list` | 2 |
| 1 | anti-spam and flood control (rate, repeats, caps, char flood) | 3 |
| 2 | invisible-character and look-alike (homoglyph) detection | 3 |
| 3 | link, IP and Discord-invite filter | 3 |
| 9 | more scanned sources (join username, command blocks, sign back side, more default scanned commands) | 3 |
| 4 | escalating punishment ladder | 4 |
| 5 | built-in mute / unmute | 4 |
| 6 | violation history log and `/abw history` | 4 |
| 7 | persistent strikes that survive restarts | 4 |
| 8 | Discord: webhook alerts, DiscordSRV hook, Discord-to-Minecraft filtering, moderation from Discord | 5 (part A) |
| GUI | `/abw` opens an in-game chest menu to configure everything, sign/chat text input | 5 (part B) |
| 11 | word-list browser | covered by the GUI (paginated word lists) |

## 6. Decisions already made with the project owner (do not re-ask)

* Storage: **plain JSON/JSONL files**, no database, no extra drivers. Atomic writes (temp file + move). History is append-only JSONL, rotated by month.
* Full parity on all four server types and all six Minecraft versions, except where impossible:
  * **DiscordSRV is a Bukkit-family plugin.** The DiscordSRV hook, Discord-to-Minecraft filtering and moderating from Discord (via DiscordSRV
    slash commands) exist only on Bukkit/Spigot/Paper/Purpur. A plain **Discord webhook** (alerts and log channel) works on all four.
    Fabric and Forge document this limit; do not fake it.
* Language word lists: public openly-licensed lists plus AI-written fill-in, every list marked `review-status: unreviewed` unless a human reviewed it.
  Credit sources in `7-wordlists/NOTICE.md`.
* Config layout: folder of files (above) **plus an in-game GUI** (session 5). The command is `/abw` (aliases `/antibadwords`, `/antiwords`).

## 7. Definition of done (every session)

* Unit tests for all new logic; the existing tests still pass.
* Real-server check for every Fabric/Forge jar you touched (`TEST=1 ./build-everything.sh ...`), and the scripted player extended and run on
  26.1 and 1.21.11 for every new player-visible behaviour.
* New settings appear in the registry, the generated default files, and `6-guides/CONFIGURATION.md`.
* `CHANGELOG.md`, the folder READMEs and the test matrix in `README.md` / `6-guides/FABRIC-AND-FORGE-STATUS.md` are updated honestly.
* A pull request to `main` from your branch with a report in the format your prompt asks for.
