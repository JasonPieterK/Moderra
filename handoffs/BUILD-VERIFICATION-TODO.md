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

## Session 3 (new filters: spam, links, bypass tricks, more scanned sources)

Same place, same limit: no Minecraft, Spigot, Fabric or Forge artifacts could be downloaded (every build host answered
HTTP 403), only JDK 21 was available. Maven Central and raw.githubusercontent.com worked.

### What was verified, and how

| Part | Level |
|------|-------|
| `5-shared-engine/src/main` (normaliser, look-alike table, word index, spam / link / evasion checks, join-name and command-block entry points, check order, `/abw check`) | **unit tests, run**: `cd 5-shared-engine && mvn -B test` -> 130 tests, 0 failures (JDK 21) |
| Speed with 10 000 words and 256-character messages | **measured** (`PerformanceTest`, 4-core container, JDK 21): word matcher p50 31 us / p99 75-92 us; with partial-match p99 78-79 us (the old scanning code: p99 ~2.9 ms); whole pipeline with every filter on p50 ~60 us / p99 219-260 us. Budget (p99 < 1 ms) is asserted. The slowest single measurement is 1-5 ms (garbage collector), so a server with a very small heap should be watched. |
| No false positives | **tested**: 10 clean-language families (Latin with and without accents, Cyrillic, Greek, Arabic/Persian, Hebrew, Indic, Thai, CJK, emoji), every bundled word list compared decision-by-decision against the unchanged old matcher over ~60 000 random and nasty inputs, and the session-2 migration comparison test still passes |
| `2-plugin-bukkit-purpur/src/main/java` (sign side, `ServerCommandEvent` for command blocks, join check, `canRewrite`) | compiled with `javac` against the **hand-written Bukkit stub** (extended with `block.sign.Side`, `block.Block`, `command.BlockCommandSender`, `entity.minecart.CommandMinecart`, `event.server.ServerCommandEvent`, `SignChangeEvent#getSide`). Not compiled against spigot-api. **Never run.** |
| `5-shared-engine/src/mc/java`, `3-mod-fabric/src`, `4-mod-forge/src` (join hook, new permission nodes) | compiled with `javac` against the **hand-written Minecraft/Fabric/Forge stub**, both `sign-array` and `sign-record`. **Never run.** |
| `live-player-test/bot.js` "filters" mode (14 new checks, 15 with `BOT_NAME`) | **only `node --check`**. Never run. |
| Jars in `1-download-jars/` | untouched (still version 1). |

### To do, in this order

- [ ] `cd 5-shared-engine && mvn -B test` (expect 130 tests green; the speed test prints its numbers).
- [ ] Build the plugin and the mods as in the session-2 list above; nothing was added to the build files, so the same
      steps apply. Watch for: `SignChangeEvent#getSide()` and `org.bukkit.block.sign.Side` (Bukkit 1.20+, assumed to
      exist on all six versions), `ServerCommandEvent` being fired for command blocks and command-block minecarts
      (assumed; if a server does not, the command-block scan silently does nothing - say so in the docs then),
      `CommandMinecart#getWorld()/getUniqueId()`.
- [ ] Real-server check of the new filters. They are off by default, so prepare the config as
      `5-shared-engine/live-player-test/README.md` ("filters mode") describes, then
      `cd 5-shared-engine/live-player-test && node bot.js localhost <port> 26.1 extras,filters` on Fabric and Forge
      26.1 and 1.21.11 (and on Paper if a server jar can be downloaded), plus one run with
      `BOT_NAME=xXshitXx ... filters` for the join-name check.
- [ ] Check on a real server that **"censor" really rewrites chat on Bukkit** (`AsyncPlayerChatEvent#setMessage` with
      `filters.spam.caps.action: censor`) and that it **blocks** on Fabric/Forge, and that `/abw check` and `/abw info`
      print the new lines.
- [ ] Check that a **command block** with a blocked word is stopped on Bukkit and that the console reports it only once
      per `filters.command-blocks.report-interval` while a repeating block keeps running.
- [ ] Check the **back side of a sign** on Bukkit (place a sign, write on the back, switch `filters.scan.sign-back` off
      and on) and that on Fabric/Forge both sides are blocked with `scan.signs` alone.
- [ ] Check an **upgraded install**: start with a version-1 `config.yml`, confirm `filters.scanned-commands` in the new
      `config.yml` is the old 13-command list, that `/team add <word>` is NOT blocked, and that nothing new fires in chat.

### Left undone on purpose (needs an API that is not used anywhere in this repository, so it was not guessed)

- [ ] **Fabric + Forge: the sign packet's front/back flag.** `filters.scan.sign-back` has no effect on mods: the
      `ServerboundSignUpdatePacket` handler is injected for whichever side was edited, so both sides are scanned under
      `filters.scan.signs`. Fix: read the packet's "is front" flag (field/accessor name differs per version; check with
      `javap` for 1.21.11 and 26.x) in the `src/mc-variants/sign-*` shims, pass it to `ModRuntime.blockSign` and send
      `Message.SIGN_BACK` for the back side. Then remove the "switch has no effect" rows from
      `6-guides/FABRIC-AND-FORGE-STATUS.md` and the `GenerateDocs` table (run `./generate-docs.sh`).
- [ ] **Fabric + Forge: command blocks.** Not scanned. The engine side is ready
      (`engine.blockCommandBlock(world, where, commandLine)`, unit-tested); what is missing is a Mixin on the
      command-block command source (or on the `ServerboundSetCommandBlockPacket` handler, which would also catch
      *editing* a block, something Bukkit cannot do). No such mixin target is used anywhere in this repository, so none
      was guessed.
- [ ] **Rewriting chat text on Fabric/Forge** (so `censor` can clean instead of block). Needs an event API that is not
      in this repository; the engine already asks `Platform.canRewrite(source)`, so only `ModPlatform` has to say true
      and the chat entry point has to pass the new text on.
- [ ] **Bukkit: scanning the *editing* of a command block.** Bukkit has no event for the command-block edit packet, so
      the command is checked when the block runs it. A player can therefore save a command with a blocked word; it never
      runs. Document, do not fake.
- [ ] **Join-name kick on mods** uses the vanilla `/kick` console command (`Platform.kick`, never run until now - this
      is the first feature that calls it). Check the kick message formatting on a real server.
- [ ] **Unicode confusables data**: unicode.org was unreachable, so the table was built from the ICU repository's copy of
      `confusables.txt` 18.0.0 (see `7-wordlists/NOTICE.md`). Re-run `5-shared-engine/generate-confusables.py` against
      the official file when the network allows, and check the diff is empty.

## Session 4 (moderation adapters, mini task 8)
- [ ] Bukkit: permissions `antibadwords.mute/.history/.discord/.gui/.bypass.mute` in `plugin.yml` (stub-compiled only). Check `/lp`/`/perm` sees them and defaults (op / nobody) are right.
- [ ] Bukkit: `BukkitPlatform.kick` now goes through `runSync`; confirm a ladder `kick` from an async chat event kicks with the message.
- [ ] Forge: the new `PermissionNode`s (`mute history discord gui bypass.mute`) register at gather time; check with a permission mod.
- [ ] Fabric: new nodes are passed to the Permissions API by reflection (unchanged code path); check with LuckPerms.
- [ ] Fabric/Forge: ladder `kick` uses the vanilla `/kick` command; check message formatting. Mute checks for signs/books/commands rely on existing `ModRuntime` calls to `inspect`.
- [ ] Run `live-player-test/bot.js ... moderation`, then restart the server, then `moderation-again` (written, only `node --check`ed). Needs the config in `live-player-test/README.md`.
- [ ] Not done (no API in the repo): mute enforcement on Fabric/Forge for the `world` name, and a GUI/Discord permission check on mods beyond nodes.
## Mini task 18 (Fabric/Forge GUI glue, chat prompt capture, /abw gui)

Read/stub-compiled only (javac against the scratchpad stub, both sign variants, no stub additions needed; never run).
Done and safe: `ModRuntime.blockChat` returns true without `onViolation` when `verdict.captured()`; `mod/gui/ModGui` (sessions,
click mapping by vanilla click-type NAME, text prompts via `engine.prompts()`, close/quit); `Platform.supportsMenu/openMenu` on
`ModPlatform`; Forge node `antibadwords.gui` in `ForgePermissions`. `ModGui.available()` is false until a loader calls
`ModGui.install(GuiBackend)`, so `/abw` behaves as before on mods.

**NOT done: the `GuiBackend` implementation (the vanilla chest menu).** Needs, to verify with `javap` on 1.21.11 AND 26.x (Mojang names):

- [ ] `net.minecraft.world.inventory.ChestMenu`: static `sixRows(int containerId, Inventory playerInventory, Container container)` or the constructor with `MenuType.GENERIC_9x6`; the `clicked(int slotId, int button, ClickType, Player)` method (override to do nothing except call `ModGui.onClick(serverPlayer, slotId, clickType.name(), button)` when `0 <= slotId < 54`, then resync the carried stack); `quickMoveStack(Player, int)` must return `ItemStack.EMPTY`; `removed(Player)` -> `ModGui.onClosed`; `stillValid`.
- [ ] `net.minecraft.world.inventory.ClickType` enum names (PICKUP, QUICK_MOVE, SWAP, CLONE, THROW, QUICK_CRAFT, PICKUP_ALL): `ModGui.mapClick` matches on those names. Check that drag (QUICK_CRAFT) and double click (PICKUP_ALL) cannot move items.
- [ ] `net.minecraft.world.SimpleContainer(int)`, `setItem(int, ItemStack)`; `net.minecraft.world.SimpleMenuProvider(MenuConstructor, Component)`; `ServerPlayer.openMenu(MenuProvider)` (return type differs between versions); `closeContainer()`; `containerMenu`, `broadcastChanges()` / `sendAllDataToRemote()` for `update`.
- [ ] Item building: `new ItemStack(ItemLike)`, `Items.*` constants for each `Icons` name (data-driven mapping table icon name -> item id via the item registry lookup with `Identifier`, which replaced `ResourceLocation` on 26.x), custom name `DataComponents.CUSTOM_NAME`, lore `DataComponents.LORE` with `ItemLore(List<Component>)`, glint `DataComponents.ENCHANTMENT_GLINT_OVERRIDE`; optionally hide vanilla tooltip lines (`DataComponents.TOOLTIP_DISPLAY`, 1.21.5+).
- [ ] Title change: `update` cannot change the title of an open menu; either reopen the menu (cursor resets) or only update items.
- [ ] Colour codes: engine texts use `§`; `Component.literal` renders them on chat (as `ModPlatform.sendMessage` relies on); check item name and lore too.
- [ ] Hook: Fabric and Forge must call `ModGui.install(new ChestBackend())` at startup. Disconnect already calls `ModRuntime.onQuit`, which calls `ModGui.onQuit`.
- [ ] Run: open `/abw`, try every click type, shift-click from the inventory, number keys, drag, drop; confirm nothing can be taken or inserted; Esc with unsaved changes; disconnect while the menu is open; chat prompt (the answer must not appear in chat, a swear word as answer gives no strike) and `cancel`.
- [ ] Sign-editor prompt (B2 sign path) on mods: `supportsSignPrompt` is false on mods; needs the open-sign-editor and block update packets, not designed here.
- [ ] `plugin.yml` permission `antibadwords.gui` (default op) is a Bukkit-side item, not done here.
## Mini tasks 11/12 (DiscordSRV hook)
- [ ] `2-plugin-bukkit-purpur` `DiscordSrvHook` / `DiscordSrvListener`: compiled only against the hand-written Bukkit stub and two name-only DiscordSRV shim
      classes. Test on a real server WITH DiscordSRV 1.x and a bot: alerts reach the channel, a blocked word typed in Discord is not shown in game, `!abw` commands
      (all off by default), no stack traces when DiscordSRV is missing.
- [ ] `2-plugin-bukkit-purpur/pom.xml`: new build-helper execution `add-discordsrv-shim` and `maven-jar-plugin` excludes `github/**`. Run `mvn package` and check
      with `unzip -l` that no `github/scarsz` class is inside the jar and that the jar loads without DiscordSRV.
- [ ] Reflection names that could differ per DiscordSRV version are listed in the `DiscordSrvHook` class comment.

## Mini task 21 (EssentialsX hook, Bukkit)
Not run against a real EssentialsX / EssentialsX Chat. Check on a real server: `EssentialsHook` reflection names (see its javadoc), the chat order
(`Chat check priority: LOWEST` log line, blocked chat never reaches EssentialsX Chat formatting, censored chat is formatted clean),
`loadbefore: [EssentialsChat]` in plugin.yml, mute-mode essentials/both (`/mute` in Essentials vs `/abw mute`), `/nick`, `/vanish` broadcasts.
## Mini task 24 (GUI editors, edit rights, stubs)
- [ ] Bukkit: `antibadwords.gui.edit.<category>` nodes are registered with default TRUE after every reload (`AntiBadWordsPlugin.registerProfilePermissions`, stub-compiled only). Check with LuckPerms that negating one node blocks only that category and that `/abw gui ladder`, `/abw gui spam` open the right page.
- [ ] Fabric/Forge: edit nodes use `ModPermissions.fallback` (true); Forge does not register them (unregistered nodes use the fallback). `ModGui.open(player, page)` is stub-compiled only; mods still have no chest backend.
- [ ] Run `tools/stub-compile.sh` (hand-written stubs, see `tools/api-stubs/README.md`): passing it proves nothing about the real APIs. It found a duplicate `case` in `ForgePermissions` (fixed).

## Mini task 32 (Discord censor, IP API)
- [ ] CI javap check of DiscordSRV 1.x: `github.scarsz.discordsrv.api.events.DiscordGuildMessagePostProcessEvent` must exist with `String getProcessedMessage()`,
      `void setProcessedMessage(String)` and `void setCancelled(boolean)` (Cancellable); `DiscordGuildMessagePreProcessEvent#getMessage()` (JDA Message,
      `getContentRaw()`). If absent the hook logs one line and `censor` blocks. Check on a real server that the censored text appears in game, and that the
      processed message really contains the raw Discord text (otherwise nothing is rewritten: the pending entry expires after 30 s and the text goes out unchanged, so test it).
- [ ] Bukkit `Player#getAddress().getAddress().getHostAddress()` (BukkitPlatform#playerIp) stub-compiled only; mods keep `playerIp` = null (no connection address call exists in the repo).
- [ ] ipban templates now use `{ip}` (falls back to the name): confirm each ban plugin's ipban command accepts an IP (EssentialsX banip, LiteBans ipban, AdvancedBan ipban, LibertyBans ipban, vanilla ban-ip).
