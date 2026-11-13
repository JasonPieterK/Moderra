# Mini-task progress (sessions 4 and 5)

Notes from each mini task for the ones that follow: what was added, decisions, what is left. Append, do not rewrite.

## Mini task 1: persistent weighted strikes (session 4, task 1) - engine only

Verification level: engine unit tests run (`cd 5-shared-engine && mvn -q -B test`: 151 tests, 21 new). Adapters were not
changed and not compiled; they already call `engine.onJoin`, `engine.onQuit` and `engine.shutdown`, which is all this needs.

### Added (package `engine.moderation`)
- `DataFile`: one JSON state file in `data/`. `load()` (returns `null` for missing/empty/damaged; a damaged file is copied to
  `<name>.corrupt-<yyyyMMdd-HHmmss>` and a loud warning is logged), `changed(delayMillis)` (debounced save through a
  `DataFile.Scheduler`), `markChanged()`, `flush()` (write now if changed; temp file + atomic move), `toJson(Object)`.
  JSON is written by a small built-in writer and read with SnakeYAML (no Gson dependency was added). The root must be an
  object with a numeric `version`. **Reuse this for `mutes.json`.** Rule: never call `changed()`/`load()` while holding the
  owner's lock (the save may run inline and takes the locks in the other order).
- `PersistentStrikeService implements StrikeService`: points per UUID, `Options` record read on every call (expiry, decay
  interval, decay points, cap, save delay, save on/off). Extra methods: `load()`, `flush()`, `shutdown(boolean save)`,
  `all()` (UUID -> points), `lastStrike(UUID)`. File `data/strikes.json`:
  `{"version":1,"strikes":{"<uuid>":{"points":3,"last":<ms>,"decay-from":<ms>}}}`.
- `PlayerDirectory`: `data/players.json` (`{"version":1,"players":{"<uuid>":{"name":"Bob","last-seen":<ms>}}}`).
  `seen(id,name)`, `learn(id,name)`, `touch(id)`, `get(id)`, `find(name)` (case-insensitive, exact spelling preferred, then
  last seen), `names(prefix, limit)`, `size()`, `load()`, `flush()`.
- `ModerationSettings` (file `moderation/ladder.yml`, category "Strikes"): `strikes.save-to-disk` (true),
  `strikes.decay.every` (0s = off), `strikes.decay.points` (1), `strikes.max-points` (0 = no limit), `strikes.save-delay` (5s),
  `known-players.forget-after-days` (0 = never). The existing `punishment.violations-expire-minutes` and
  `punishment.clear-violations-on-quit` are unchanged and still used.

### Engine changes
- `Engine` installs the `PersistentStrikeService` by default (`InMemoryStrikeService` still exists; `setStrikeService` still works).
- New: `engine.players()`, `engine.resolvePlayer(name)` (online exact name first, then the directory), `engine.isOnline(uuid)`.
- `onJoin` -> `players.seen`, `onQuit` -> `players.touch`, a counted violation -> `players.learn`.
- `shutdown()` writes strikes (when they are kept) and players, then empties memory.
- `/abw violations <player>` and `/abw resetviolations <player>` accept offline players (answer ends with a grey " (offline)");
  tab completion also offers up to 50 known offline names. Unknown name: "Player not found (not online and never seen on this
  server)." (was "Player not online."; `CommandsTest` updated).

### Decisions
- Semantics: expiry = everything is forgotten when the LAST strike is older than `violations-expire-minutes` (unchanged, same
  boundary as before). Decay = lose `points` every `every`, counted from the player's first point (a new strike does not
  restart the interval), also while the server is off. Both are computed lazily from stored times; no background task.
- Default behaviour identical: `clear-violations-on-quit` stays true by default, and while it is true strikes are neither
  written nor read (nobody offline has strikes, so there is nothing to keep; this also keeps "gone after a crash" as before).
  Strikes survive a restart when `clear-violations-on-quit: false` and `strikes.save-to-disk: true`.
  Known edge: a `strikes.json` written earlier stays on disk untouched while clear-on-quit is true and is read again if the
  admin later switches clear-on-quit off and restarts (expiry/decay still apply to it).
- `ViolationHandler` still resets the player's strikes after a punishment (legacy one-step behaviour). The ladder task must
  decide what replaces this.
- Weights: nothing new was needed; `Hit.strikeWeight` and the per-check `strike-weight` settings already exist (session 3).
- Saving goes through `platform.runSync -> runSyncLater(delay) -> runAsync`; a scheduler that refuses tasks (server stopping)
  is tolerated, shutdown writes synchronously.

### Left for later mini tasks
- Ladder, mute, history, `/abw strikes`, `/abw ladder`, guides (`6-guides/MODERATION.md`, README, CHANGELOG), bot.js.
- Mute task: `known-players.forget-after-days` removes name -> UUID entries by last-seen only; if mutes are looked up by name,
  keep muted players findable (for example look in `mutes.json` first, or exempt muted players from pruning).
- Not verified on a real server (no server in this environment): the timing of the debounced save on Bukkit/Fabric/Forge.
