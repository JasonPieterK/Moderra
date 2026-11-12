# Build and server verification still to do

Some sessions of this feature round ran in a place that **could not download** Minecraft, the Spigot API, Fabric (Loom,
Fabric API) or Forge, and had only JDK 21. They could build and test the pure-Java engine, nothing else. This file is the
exact list of what was therefore **not** compiled or run. If you have network access: work through it, fix what breaks,
tick the boxes, and update the honest test matrix in `README.md` and `6-guides/FABRIC-AND-FORGE-STATUS.md`.
Later sessions: **append your own section** at the end; do not delete open items.

## Session 2 (shared engine, folder config, migration, world profiles, /abw lang, /abw whitelist)

### What was verified, and how

| Part | Level |
|------|-------|
| `5-shared-engine/src/main` (engine, registry, loader, YAML editor, migration, profiles, commands) | **unit tests, run**: `cd 5-shared-engine && mvn -B test` -> 88 tests, 0 failures (JDK 21) |
| `2-plugin-bukkit-purpur/src/main/java` (5 classes) | compiled with `javac` against a **hand-written stub** of the ~50 Bukkit classes it uses. Not compiled against spigot-api. Never run. |
| `5-shared-engine/src/mc/java`, `src/mc-variants`, `3-mod-fabric/src`, `4-mod-forge/src` | compiled with `javac` against a **hand-written stub** made from how the old code used Minecraft/Brigadier/Fabric/Forge (both `sign-array` and `sign-record`). Not compiled against the real APIs. Mixins not processed. Never run. |
| `pom.xml` (plugin), `build.gradle` (Fabric, Forge), all `*.sh` | **only read** (`bash -n` for syntax). Never executed. |
| `live-player-test/bot.js` additions | **only read** (`node --check`). Never run. |
| Jars in `1-download-jars/` | untouched: still the version-1 builds from before this session. |

### To do, in this order

- [ ] **Engine tests on your machine**: `cd 5-shared-engine && mvn -B test` (expect 88 tests green).
- [ ] **Plugin compiles for every version**: `./2-plugin-bukkit-purpur/build-all-versions.sh` (needs JDK 21 + 25).
      Things to look at if it fails:
      * the pom adds `../5-shared-engine/src/main/java` with `build-helper-maven-plugin` and three `<resource>` entries
        (plugin.yml filtered; engine resources; `../7-wordlists/*.yml` -> `wordlists/`);
      * `-Xlint:all` now also sees the engine sources (warnings are fine, errors are not);
      * SnakeYAML is declared `provided` 2.3 next to the one spigot-api brings; remove the explicit one if Maven complains.
- [ ] **Check the plugin jar content**: `unzip -l "1-download-jars/Antibadwords-RELEASE[Bukkit] - 1-26.3.jar"` must show
      `plugin.yml`, `wordlists-index.txt`, `abw-defaults/punishment-*.yml` (4 files), `wordlists/en.yml` ... (38 files),
      `com/jason/plugins/antibadwords/engine/**`.
- [ ] **Fabric and Forge compile**: `./build-everything.sh 26.3 26.1 1.21.11`, then all versions.
      Things to look at if it fails:
      * `build.gradle` (both): source roots are now `../5-shared-engine/src/main/java`, `src/mc/java`,
        `src/mc-variants/sign-<api>`; resources `../5-shared-engine/src/main/resources` + `src/mc/resources`;
        `processResources { from('../7-wordlists') { include '*.yml'; into 'wordlists' } }`;
      * `ForgePermissions.register()` now calls `event.addNodes(nodes.toArray(new PermissionNode<?>[0]))` (the old code
        called `event.addNodes(ADMIN, NOTIFY, BYPASS)`; if `addNodes` is not a varargs method, adapt this one line);
      * `ModRuntime` got a nested `ModPlatform implements Platform`; it uses only Minecraft calls the old file already used
        (`getPlayerList().getPlayers()/getPlayer(uuid)/getPlayerByName`, `sendSystemMessage`, `server.execute`,
        `performPrefixedCommand`, ...).
- [ ] **Check a mod jar content** the same way (word lists under `wordlists/`, `wordlists-index.txt`, `abw-defaults/`).
      On Forge also check that the files are readable at runtime: the engine uses
      `Resources.class.getResourceAsStream("/wordlists/en.yml")` (the old code read `/config.yml` the same way).
- [ ] **Real server check, Fabric and Forge**: `TEST=1 ./build-everything.sh 26.3 26.1 1.21.11` (then all 24).
      The scripts now type 15 commands (plus `stop`) and make 19 checks per jar (was 9; 20 with `OLD_CONFIG=1`), including: config folder created, word lists
      copied, `/abw lang list|enable|disable`, a German word blocked while `de` is on, `/abw whitelist add|list|remove`,
      `/abw reload`, and "no config problems in the log".
- [ ] **Migration on a real server**: `OLD_CONFIG=1 ./3-mod-fabric/test-on-real-server.sh <args>` and the same for Forge
      (starts with the version-1 `config.yml`; expects the migration log lines and the same languages on).
      Arguments: see the `VERSIONS` line of the Minecraft version in `build-all-versions.sh`.
- [ ] **Bukkit / Paper / Purpur real server** (never done, also not for version 1): get a Paper or Purpur 26.x server jar,
      then `SERVER_JAR=/path/paper.jar ./2-plugin-bukkit-purpur/test-on-real-server.sh 26.3` and again with `OLD_CONFIG=1`.
      **This script has never been run**; expect small fixes (console flag `--nogui`, warm-up time, log wording).
- [ ] **Scripted player** on Fabric and Forge 26.1 and 1.21.11 (and on Paper if possible):
      `cd 5-shared-engine/live-player-test && npm install && node bot.js localhost <port> 26.1 extras`.
      7 new checks for `/abw lang` and `/abw whitelist` were added and never run. One of them looks for the new word at the
      end of a chat line (`/botsafeword$/`); adjust if the server formats the line differently.
- [ ] **Behaviour to watch on a real server** (logic is unit-tested, the wiring is not):
      * strikes, warnings, punishments still appear exactly as in version 1 (chat, `/msg`, sign, book, anvil);
      * anvil: one strike per anvil session, result slot stays empty;
      * Bukkit: chat is checked on the async chat thread, the follow-up runs on the main thread (`BukkitPlatform.runSync`);
      * mods: `ModRuntime.remember(server)` must have run before the first broadcast (it is called from every entry point;
        on Fabric also at `SERVER_STARTED`);
      * Bukkit: an operator must **not** get a `worlds.yml` profile just for being op (the plugin registers
        `antibadwords.profile.<name>` with default `false` after every reload);
      * attribute punishments and the crash ledger still restore values (unchanged code, but re-wired).
- [ ] After building: copy the new jars into `1-download-jars/` (the build scripts do that) and rewrite the test matrix.

### Left undone on purpose (needs an API that is not used anywhere in this repository, so it was not guessed)

- [ ] **Fabric + Forge: world name for `worlds.yml` rules.** `ModPlatform.worldName()` returns `""` and
      `ModRuntime.message(...)` passes `""` as world, so on mods only profiles by permission and the default profile
      work. Fix: in `ModRuntime.message` pass the dimension id of `player.level()` as world (and/or
      `.withDimension(...)`), for example `minecraft:the_nether`. Look up the real method names with `javap` for 1.21.11
      and 26.x (the identifier class and its accessor were renamed around 1.21.11), add a per-version shim under
      `src/mc-variants/` if they differ, then remove the "Limits" sentences in `GenerateDocs` (run `./generate-docs.sh`),
      `CHANGELOG.md` and `6-guides/FABRIC-AND-FORGE-STATUS.md`.
- [ ] **Forge: permission nodes for profiles added after server start.** Forge gathers nodes once; a profile added to
      `worlds.yml` later has no node until the next restart (nobody has the permission until then). Documented, not fixable
      without a Forge API for late registration.
- [ ] **Censoring on mods.** No check censors yet. When one does (session 3), the mods and the non-chat Bukkit events treat
      "censor" as "block" (`ModRuntime.apply`, `AntiBadWordsListener`). Rewriting chat text on Fabric/Forge needs event APIs
      that are not in this repository yet.
- [ ] **World-profile difference on a live server** (two worlds with different profiles): not scripted; covered by
      `WorldProfilesTest` only.
- [ ] **`Platform.kick` on mods** runs the vanilla `kick <name> <reason>` console command (no networking API guessed).
      Nothing calls `kick` yet; check it when the first feature uses it.
