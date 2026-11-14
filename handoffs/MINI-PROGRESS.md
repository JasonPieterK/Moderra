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

## Mini task 2: duration parser and escalation ladder (session 4, task 2, first half) - engine only

Verification level: engine unit tests run (`cd 5-shared-engine && mvn -q -B test`: 171 tests, 20 new; `LegacyMigrationTest`
unchanged and passing). Adapters not changed and not compiled.

### Added (package `engine.moderation`)
- `DurationParser`: `parse(String)` -> milliseconds or `PERMANENT` (-1) for `perm` / `permanent` / `forever`; `tryParse`,
  `isPermanent(long)`, `format(long)` (`1h30m`, `permanent`). Wraps `DurationText` but REQUIRES a unit (`90` is refused, so
  `/abw mute Bob 90 ...` cannot mistake a reason word for a time). Units `ms s m h d w`, parts of at most 9 digits.
- `MuteHandler`: `boolean mute(UUID, name, durationMillis|PERMANENT, reason, actor)`; `MuteHandler.SYSTEM = "system"`.
  `engine.muteHandler()` / `engine.setMuteHandler(handler)`. Default: logs a warning, returns false. **The mute task
  implements this and calls `engine.setMuteHandler(...)`.**
- `Ladder` (immutable, one per profile: `EffectiveSettings.ladder()`): `Ladder.parse(rawSection, warn)`, `Ladder.action(line)`,
  `steps()` (sorted by threshold), `isEmpty()`, `reached(before, after)`, `next(points)` -> `Optional<Next(step, at)>`,
  `limit(points)`, `firstThreshold()`. Records `Step(name, threshold, repeatEvery (0 = once), message, broadcast, actions)`
  and `Action(type, args, text)`. `next()` is what `/abw strikes` needs; `steps()` what `/abw ladder` needs.
- `LadderSettings` (file `moderation/ladder.yml`, category "Ladder", all per-profile): section `ladder.steps` (default `{}`),
  `ladder.when-several-steps-are-reached` (`all` | `highest`, default all), `ladder.default-reason` ("Bad language"),
  `ladder.ban-command` ("ban {player} {reason}").

### Step format
```yaml
ladder:
  steps:
    short-mute:            # free name
      at: 4                # strikes
      repeat: once         # or a number N / "every N": again at 4+N, 4+2N ...
      message: "&c..."     # to the player (optional)
      broadcast: "&7..."   # to everyone (optional)
      actions:             # top to bottom
        - mute 10m Bad language
```
Actions: `warn [text]`, `mute <time|perm> [reason]`, `kick [reason]`, `ban [reason]` (runs `ladder.ban-command`),
`command <console command>`, `effect <name> <level> <seconds>`, `attribute <name> <value> <seconds>`,
`sound <name> <volume> <pitch>` (these three go to `Platform.applyAction` with the step name as id), `punishments` (every
enabled classic action under `punishment.effects/attributes/sounds/commands`), `reset-strikes` (alias `reset`).
Placeholders: `{player} {uuid} {word} {source} {count} {step} {threshold} {reason} {duration}`, in messages also
`{punishments} {limit} {remaining}`. Wrong steps/actions are left out and reported as load warnings.

### Decisions
- **No steps (default) = the classic flat flow, code path untouched**: punish at `violations-before-punish`, strikes reset.
  Only addition: its `punishment` event now also carries `step: legacy` and `threshold`.
- **With steps, the ladder replaces the flat flow for that profile** and strikes are NOT reset after a step (they go down by
  decay / expiry / `/abw resetviolations`, or by the explicit `reset-strikes` action). `punishment.final-command` and the
  `punishment-broadcast/-player` messages are not used in ladder mode (a step has its own message/broadcast/command).
  `punishment.enabled: false` also switches the ladder off (player gets `punishment-disabled-player` from the first threshold on).
- The classic setup as one step is `at: <violations-before-punish>`, `repeat: once`, `actions: [punishments, reset-strikes]`
  plus the message (tested: same actions, same strike reset). **Mini task 3 can use exactly this for the automatic migration**;
  it still has to carry over the punishment broadcast message and the final command (as a `command` action).
- A step fires when strikes go UP past a trigger point: `before = count - weight`, so a player sitting at `strikes.max-points`
  still re-triggers the steps in the last window. Passed several times by one change = once. Reached again after fading.
- Order inside a step: actions top to bottom, then broadcast and message, and kick/ban last (so the player sees the message).
- Between steps the player gets the normal `warning-player` message; `{limit}` = next trigger point, `{remaining}` = distance
  to it. When no further step exists `{remaining}` is 0 (make the last step repeating to avoid that).
- One `punishment` event per step that ran: data `step`, `threshold`, `actions` (texts joined with "; "), `reason`, `duration`,
  `uuid`, `punishments`, `count`, `profile`, `word`, `source`.
- The words "a warning", "a mute (10m)", "a kick", "a ban" in `{punishments}` are fixed English like the existing
  "server command"; not configurable yet.

### Left for later mini tasks
- Mini task 3: automatic migration of the flat setup into `ladder.steps`, `/abw ladder`, `/abw strikes`.
- Mute service (implements `MuteHandler`), history, adapters (nothing new needed for kick: `Platform.kick` exists), guides,
  CHANGELOG, engine README, bot.js. Nothing was verified on a real server.
