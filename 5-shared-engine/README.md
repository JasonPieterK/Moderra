# 5-shared-engine - the brain every server type shares

One piece of plain Java that decides everything: is this text allowed, how many strikes does the player have, what
message is sent, what the config files look like. The Bukkit plugin, the Fabric mod and the Forge mod all compile these
same files into their jar, so all four server types behave the same.

| Folder | What it is | Tested here? |
|--------|-----------|--------------|
| `src/main/java/.../engine/` | the engine (no Bukkit, no Minecraft): checks, strikes, messages, config folder, migration, `/abw` commands | **yes** - `mvn test` |
| `src/main/java/.../` (`WordMatcher`, `Placeholders` ...) and `.../mod/` (`Names`, `AttributeLedger`) | older pure classes the engine uses (they kept their package names) | **yes** |
| `src/main/resources/` | default punishment sections and `wordlists-index.txt` (the list of language files in the jar) | yes |
| `src/mc/java`, `src/mc-variants` | the parts of the **mods** that touch Minecraft (shared by Fabric and Forge) | only by a real Fabric/Forge build |
| `src/test` | the unit tests, the old `config.yml` used by the migration tests, and the unchanged old code as a test oracle | |
| `live-player-test/` | a scripted player for real-server tests | needs a real server |
| `generate-docs.sh` | rewrites `6-guides/CONFIGURATION.md` and `wordlists-index.txt` | a test fails if you forget |

```bash
cd 5-shared-engine && mvn test        # all unit tests (needs only Maven and a JDK 21+)
./generate-docs.sh                    # after adding/changing a setting or adding a language file
```

## How a loader uses the engine

```java
Engine engine = Engine.load(platform, configFolder);            // creates / migrates / reads the config folder
Message m = engine.message(playerId, playerName, Message.CHAT, text);
Verdict v = engine.inspect(m);                                  // no side effects, any thread
if (v.blocked()) cancel();  else if (v.censored()) replaceTextWith(v.text());
engine.onViolation(m, v);                                       // strikes, warnings, punishments, events (moves itself to the main thread)
```

`Platform` is the small interface each loader implements (players, messages, console commands, permissions, scheduler,
effects/attributes/sounds). Sign lines and book pages go through `engine.inspectFirst(template, texts)`.

## How to add things (for the next features)

| I want to add... | Do this |
|------------------|---------|
| a setting | `Setting.bool("filters.spam.enabled", true).file("filters/spam.yml").category("Spam").title(..).description(..).overridable().build()` as a constant, register it in a `static void register(SettingsRegistry r)` method, and call that method in `Engine.registerSettings`. The file, its comments, validation and `CONFIGURATION.md` follow automatically (run `./generate-docs.sh`). |
| a check (spam, links ...) | implement `TextCheck` (`id()`, `enabled(settings)`, `check(message, settings)` returning a `Hit`) and call `engine.registerCheck(order, check)` in `Engine.registerBuiltIns`. Read settings with `settings.get(MY_SETTING)`; cache compiled things with `settings.derived("id.what", s -> ...)`. |
| a `/abw` sub-command | one class implementing `SubCommand`, registered with `commands.register(new MyCommand())` in `CoreCommands.register` |
| a reaction to events (history, Discord) | `engine.events().subscribe(event -> ...)`; kinds so far: `violation`, `punishment`, `admin-command`, `spam`, `link`, `evasion` |
| another strike counter | `engine.setStrikeService(new MyStrikeService())` |
| state file in `data/` | `engine/moderation/DataFile` (JSON, temp file + move, debounced save, `.corrupt-<date>` copy of a damaged file) |
| find a player who is offline | `engine.resolvePlayer(name)` / `engine.players()` (`data/players.json`) |
| a new text source (join name ...) | `engine.registerSource("join", SCAN_SWITCH_SETTING)` and send `Message`s with that source |
| a programmatic config change | `engine.set(setting, value)`, `engine.addToList(...)`, `engine.removeFromList(...)` or `engine.config().edit(file, editor -> ...)` - they use `YamlEditor`, which keeps comments |

## Moderation (package `engine.moderation`)
`PersistentStrikeService`, `Ladder`, `MuteService` (`engine.mutes()`), `HistoryStore` (`engine.history()`), `PlayerDirectory`,
`DurationParser`, `DataFile`. Adapters only need `engine.onJoin/onQuit/shutdown`, `inspect` for every text source and the
permissions `antibadwords.mute/.history/.bypass.mute`. See `6-guides/MODERATION.md`.
Tests: every test that starts an engine goes through `TestEngines.track` (global JUnit extension) so engines are shut down
before the `@TempDir` is deleted.

## The filters (package `engine.filters`)

| Class | What it does |
|-------|--------------|
| `EvasionNormalizer`, `Confusables` | turn text into canonical tokens for `WordMatcher` (invisible characters, compatibility forms, accents, look-alikes, leetspeak); settings in `EvasionSettings`; default table `abw-defaults/look-alikes.yml`, rebuilt with `generate-confusables.py` from Unicode's `confusables.txt` |
| `WordIndex` | trie + bit sets so masked and partial matching do not scan every word (`PerformanceTest` holds the 1 ms budget) |
| `EvasionCheck` (`evasion`), `LinkCheck` + `LinkParser` (`link`), `SpamCheck` (`spam-rate`, `spam-repeat`, `spam-caps`, `spam-flood`, `spam-words`, `spam-commands`) + `SpamState` | the pipeline stages; settings in `EvasionSettings`, `LinkSettings`, `SpamSettings` |

Things later features can use:

* `Hit.strikeWeight` of these checks is a setting (default 0). `ViolationHandler` adds the weights of all hits of a
  message and gives them to the `StrikeService`.
* Events: one `violation` per message as before, **plus** one event of kind `spam`, `link` or `evasion` per hit of such
  a check (data: `check`, `detail`, `action`, `verdict`, `weight`, `profile`, `text`, `player`). A bad player name is a
  `violation` with source `join` and `action` = `log` / `warn` / `kick`. Command blocks publish with `playerId == null`.
* `TextCheck.feedback(hit, settings)`: the line a player gets when that check stopped the message.
  `TextCheck.seesEveryCommand()`: the check gets every command line, not only scanned ones.
* `Message.asProbe()` / `engine.explain(text)`: judge without counting (used by `/abw check`).
* `Platform.canRewrite(source)`: where the loader can deliver a cleaned text; elsewhere the engine turns "censor" into "block".
* `engine.onJoin(uuid, name)`, `engine.blockCommandBlock(world, where, commandLine)`, `engine.registerSource(source, switch...)`,
  `engine.scans(source, settings)`, `engine.check(id)`, `engine.checkIds()`, `engine.spamState()`.
* `filters.check-order` accepts a check id or a group name (`spam` = every id starting with `spam-`).

Rules: no Bukkit or Minecraft imports in `src/main`; only SnakeYAML (and Gson) as libraries; every new behaviour gets a
setting and a unit test.
