# Session 2/5 (medium): Foundation - shared engine, folder config, settings registry, world profiles, whitelist command

## Summary
Right now the Bukkit plugin and the Fabric/Forge mods each contain their own copy of the logic (the mods are a port). Every new feature would
have to be written twice. This session builds the **platform-neutral engine** that all four loaders use, a **folder-based configuration** driven by a
**settings registry** (also the future source for the in-game GUI), automatic **migration** from the old single `config.yml`, an internal **event
stream**, **per-world rule profiles** (feature 10) and the **`/abw whitelist`** command (feature 12). Behaviour for existing installs must not
change. Sessions 3, 4 and 5 build on the names and contracts in `handoffs/00-shared-context.md` section 4, so follow them exactly. Read that
whole file first.

## Tasks
1. **Rename and extract.** `git mv 5-shared-mod-code 5-shared-engine`; fix every reference (scripts, Gradle/Maven paths, docs, READMEs, `.gitignore`).
   Move the plugin's pure-Java classes (`WordMatcher`, `Placeholders`, `PunishmentAction`, `AntiBadWordsSettings`, `PunishmentParser` logic,
   violation/expiry handling, message formatting) into the engine; the plugin and both mods compile the engine as a source root, as they already
   do for the shared classes. No Bukkit/Minecraft imports in the engine.
2. **Engine API** exactly as in the shared context: `Platform`, `Engine`, `Message`, `Verdict`, `TextCheck`/`Hit`, `engine.registerCheck`,
   `ModerationEvent` + `engine.events()`, `StrikeService` (keep today's in-memory behaviour behind it). Existing detection becomes the first
   registered check (`badword`). Reduce `AntiBadWordsListener` (Bukkit) and `ModRuntime`/mixins (mods) to thin adapters: build a `Message`, call
   `engine.inspect`, apply the `Verdict`, run punishment actions through `Platform`.
3. **Settings registry** (`SettingsRegistry`, `Setting<T>`, types BOOLEAN/INT/DOUBLE/STRING/ENUM/DURATION/STRING_LIST/SECRET with default, min/max, category,
   title, description, owning file). Register **every existing setting** from the current `config.yml`. Generate from it: the default YAML files
   with comments, `6-guides/CONFIGURATION.md` (a build-time tool, committed output), and validation errors with the offending key and file.
4. **Folder config** as laid out in the shared context. Create the loader, default-file writer (first start), `reload()`, and a **comment-preserving
   YAML editor** used for programmatic edits (try SnakeYAML 2.x comment round-trip; otherwise careful text patching; unit-test that comments and
   ordering survive). Unknown keys produce a warning, never a crash; a broken file keeps the previous good settings and reports the error.
5. **Word-list files.** The loader reads `wordlists/<code>.yml` in the format from `7-wordlists/README.md` (session 1 is creating that folder in parallel;
   if it is not on `main` yet, use the 7 legacy lists converted into that format and keep the loader format-compatible). `7-wordlists/` is copied
   into every jar's resources and into the config folder on first start (wire this into the Maven resources and both Gradle builds). Default enabled
   languages for new installs: `en` plus `custom`. Admin commands `/abw lang list|enable <code>|disable <code>` (registry-backed, saved with the
   comment-preserving editor, applies on reload). Protection-level tier logic stays as is.
6. **Migration.** If an old single-file `config.yml` (no `config-version: 2`) exists: back it up as `config.yml.bak-<date>`, create the new folder
   files with the old values, convert the old `word-lists:` and `filters.whitelist`/`custom` words into `wordlists/*.yml` and keep exactly the same
   languages enabled, then log what was migrated. Never delete the old file. Unit-test with the real old default config and with an edited one.
7. **Feature 10: world / group profiles** in `worlds.yml`: named profiles that override any *overridable* registry setting (at least protection level,
   enabled languages, every filter on/off and threshold, punishment mode, scan sources), a default profile, a mapping from world names (supports `*` globs
   and the dimension names) to profiles, and an optional per-player override through the permission `antibadwords.profile.<name>`. The engine resolves
   an `EffectiveSettings` for (world, player) once per message. Mark which registry settings are overridable in the registry itself.
8. **Feature 12: `/abw whitelist add <word>|remove <word>|list [page]`** writing to the whitelist (a registry STRING_LIST), case-insensitive, applies at once
   (no restart), with tab completion. Put commands behind the same permission checks as existing admin commands. Move `/abw` command parsing into the engine
   (`engine.commands()`), keep the existing sub-commands working, and make adding sub-commands from other sessions a one-class change.
9. **Tests and docs.** Unit tests for engine, registry, loader, migration, world profiles, whitelist, comment-preserving editor. Extend
   `5-shared-engine/live-player-test/bot.js` and the `test-on-real-server.sh` checks for `/abw lang list`, `/abw whitelist ...` and a world-profile
   difference if the test server can have two worlds (otherwise test profile resolution in unit tests and say so). Try to obtain a Paper or Purpur 26.x
   server jar and create `2-plugin-bukkit-purpur/test-on-real-server.sh` using the bot; if not possible, document why. Update docs and READMEs.

## Files
Rename `5-shared-mod-code/` to `5-shared-engine/`. Create `5-shared-engine/src/main/java/com/jason/plugins/antibadwords/engine/**`, tests under
`5-shared-engine/src/test/`. Change: `2-plugin-bukkit-purpur/src/main/java/com/jason/plugins/antibadwords/*.java` (shrink to adapters),
`2-plugin-bukkit-purpur/pom.xml`, `2-plugin-bukkit-purpur/src/main/resources/{plugin.yml,config.yml}`, `3-mod-fabric/build.gradle`, `4-mod-forge/build.gradle`,
`5-shared-engine/src/mc/java/.../{ModRuntime,AbwCommand,ModPermissions}.java`, `build-everything.sh` and the per-folder build scripts, `6-guides/*`, `README.md`,
`CHANGELOG.md`. Read first: `5-shared-mod-code/src/main/java/.../mod/ModCore.java` (the port with every rule), the plugin's `AntiBadWordsPlugin.java`,
`AntiBadWordsListener.java`, `PunishmentExecutor.java`, `AntiBadWordsCommand.java`.

## Constraints
* Existing behaviour, messages and defaults stay identical (the old config produces the same decisions). Keep the 24-jar build and jar names.
* Session 1 owns `7-wordlists/` content; you only read it and wire it in. Sessions 3/4/5 will add checks, strike/mute/history and Discord/GUI on top of your
  contracts; do not implement their features, but leave the extension points (registerCheck, events, StrikeService, command registration) clean and documented.
* Do not weaken any behaviour for the sake of the refactor; port tests first, then move code.
* Allowed libraries: SnakeYAML and Gson only.

## Acceptance criteria
- `cd 5-shared-engine && mvn test` and the plugin's `mvn clean test ...` pass; new tests cover migration (old default and edited configs), registry-generated defaults,
  world-profile resolution, whitelist, comment-preserving edits.
- `TEST=1 ./build-everything.sh 26.3 26.1 1.21.11` is clean (all 9 server checks per Fabric/Forge jar); the scripted player passes on Fabric and Forge 26.1 and 1.21.11.
- A server started with the **old** `config.yml` migrates automatically and behaves the same (show the log lines and a before/after decision comparison test).
- Fresh start creates the full config folder with commented files; `/abw reload` picks up edits; a broken file does not crash.
- Before the PR: one full `TEST=1 ./build-everything.sh` (all 24 jars) is clean, or list exactly what failed.

## Branch
`handoff/session-2-foundation`, branched from `main`. Open a pull request to `main`. **Sessions 3, 4 and 5 must start only after this PR is merged.**

## Report back
What changed and the final engine API (class and method names), the registry categories you created, migration behaviour and edge cases, test results per
loader/version, whether a real Paper/Purpur test was possible, open issues, and anything the next sessions must know.
