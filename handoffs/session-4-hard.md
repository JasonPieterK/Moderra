# Session 4/5 (hard): Moderation core - persistent strikes, escalation ladder, built-in mute, history

## Summary
Turn AntiBadWords from "one flat punishment" into a small moderation system: **(7) persistent strikes** that survive restarts and can decay, **(4) an escalating
punishment ladder**, **(5) a built-in mute/unmute**, and **(6) a violation history** with `/abw history`. All configurable, all stored as plain JSON/JSONL
(no database), identical on the plugin, Fabric and Forge. Prerequisite: session 2 is merged (`Engine`, `StrikeService`, `SettingsRegistry`, `ModerationEvent`,
`Platform`, folder config); session 3 may or may not be merged (its checks only feed `Hit.strikeWeight`). Read `handoffs/00-shared-context.md` first.

## Tasks
1. **Persistent weighted strikes (7)**, `moderation/ladder.yml` + `data/strikes.json`. Replace the in-memory `StrikeService` implementation with a persistent one: points per player UUID
   (not name), weight per check (`Hit.strikeWeight`, defaults: badword 1, others 0 unless the admin sets them), optional **time decay** (for example lose 1 point every N
   minutes/hours; also keep the existing `violations-expire-minutes` semantics working), a switch for "clear on quit" (the old default stays), a cap, atomic writes
   (temp file + move), save debounced and on shutdown, safe load of a damaged file (keep a `.corrupt-<date>` copy, start empty, log loudly). `/abw violations <player>` and
   `resetviolations <player>` keep working and also accept offline players. Maintain `data/players.json` (name to UUID, last seen) so offline lookups work.
2. **Escalation ladder (4)**. Admin-defined steps, evaluated when strikes change: for example at 2 points `warn`, at 4 `mute 10m`, at 6 `kick`, at 8 `command tempban {player} 1d {reason}`.
   A step has a threshold, a list of actions, a message, and "repeat every N points" or "once". Actions: the existing effect/attribute/sound/command actions, plus new `warn`
   (message only), `mute <duration|perm> [reason]`, `kick [reason]`, `command <console command with placeholders>`. Vanilla has no timed ban, so `tempban` is only a
   configurable console-command action (document using a ban plugin command); also offer `ban` through the vanilla command. The legacy flat setup (`violations-before-punish` plus
   the enabled actions) must still work as a one-step ladder; migrate it into `ladder.yml` automatically while keeping the same behaviour. Ladders can differ per `worlds.yml`
   profile. A `/abw ladder` command shows the active ladder; `/abw strikes <player>` shows points and next step.
3. **Built-in mute (5)**, `moderation/mute.yml` + `data/mutes.json`. `/abw mute <player> [duration] [reason...]`, `/abw unmute <player>`, `/abw mutes [page]`. Durations like `30s`,
   `10m`, `1h30m`, `2d`, `perm`. Works for offline players (via `players.json`). Enforced inside `engine.inspect` before the other checks: muted players are blocked in chat and in
   a configurable list of commands (`msg tell w r say me ...`; wildcard allowed); optionally also signs/books. Messages (muted, remaining time, unmuted, expired) are configurable,
   show the remaining time, and a muted player gets a notice on join. Expiry is checked lazily and by a periodic task; emit `mute`, `unmute`, `mute-expired` events. Permissions
   `antibadwords.mute` (and `antibadwords.bypass.mute` for exempt players), muted-by staff is stored. Wire the ladder's `mute` action to the same service.
4. **History (6)**, `moderation/history.yml` + `data/history/YYYY-MM.jsonl`. Subscribe to `ModerationEvent`s (no direct calls from checks). One JSON object per line: time, kind,
   player uuid/name, world, source, check, detail, actor (staff or "system"), points after, ladder step, and **optionally** the blocked text (setting `store-message-text`,
   default false for privacy; also a `redact-words` option that stores the text with the bad word masked). Append from a background writer (never block the server thread),
   rotate by month, retention in days (delete or archive older files), size cap. `/abw history <player> [page]` (newest first, paginated, click-free plain text), `/abw history recent [n]`,
   `/abw history purge <player>` (admin only, asks twice with a confirm argument). Permission `antibadwords.history`. Never log secrets.
5. **Settings and docs.** Register all settings (titles/descriptions are future GUI labels), regenerate `6-guides/CONFIGURATION.md`, write an ELI5 guide `6-guides/MODERATION.md`
   with example ladders for a family server, a normal server and a strict server. Update `README.md`, `CHANGELOG.md`, folder READMEs.
6. **Tests.** Unit tests with fake clock and temp dirs: decay, persistence across a simulated restart, corrupt file recovery, ladder evaluation (thresholds, repeats, per-profile),
   duration parser, mute expiry and enforcement in `inspect`, offline lookup, history writing/rotation/retention/pagination, concurrency (many threads adding strikes). Extend
   `5-shared-engine/live-player-test/bot.js` (join, get struck, reach the ladder steps, get muted, chat while muted, see the mute notice, reconnect and still be muted) and run
   it on Fabric and Forge 26.1 and 1.21.11, and on the Bukkit test server from session 2 if it exists. Restart the server mid-test to prove persistence.

## Files
Create under `5-shared-engine/src/main/java/com/jason/plugins/antibadwords/engine/moderation/**` (`PersistentStrikeService`, `Ladder`, `MuteService`, `DurationParser`,
`HistoryStore`, `PlayerDirectory`), tests under `5-shared-engine/src/test/`. Modify: the engine's `onViolation` flow, `engine.commands()` registration, the adapters that apply
punishment actions (`2-plugin-bukkit-purpur/.../PunishmentExecutor.java`, `5-shared-engine/src/mc/java/.../ModRuntime.java`) for the new `kick`/`mute`/`warn` actions, join/quit hooks,
`plugin.yml` permissions, Forge/Fabric permission registration (`ForgePermissions`, `FabricPermissions`), `6-guides/*`, `README.md`, `CHANGELOG.md`, the bot.

## Constraints
* JSON/JSONL files only, no database, SnakeYAML/Gson only. Never block the server thread on disk I/O for history; strike/mute saves are debounced and atomic.
* Existing installs must behave the same after upgrade (legacy flat punishment becomes a migrated one-step ladder; "clear on quit" default unchanged).
* Do not implement Discord or the GUI (session 5); expose everything through `ModerationEvent`s and registry settings so they can plug in. Do not rename session-2 APIs.
* Session 3 may run in parallel and touches the filters and the adapters; keep your adapter edits small, rebase on `main` often, resolve conflicts by keeping both behaviours.
* Privacy: history may contain chat text; default off, documented. Only staff with `antibadwords.history` can read it.

## Acceptance criteria
- `cd 5-shared-engine && mvn test` and the plugin's `mvn clean test ...` pass with the new tests (list the counts).
- `TEST=1 ./build-everything.sh 26.3 26.1 1.21.11` is clean; the scripted player proves strikes persist across a server restart, the ladder fires in order, mute blocks chat and
  chosen commands and survives reconnect, `/abw history` shows the events, on Fabric and Forge 26.1 and 1.21.11.
- An old config with flat punishment behaves exactly as before (test it).
- A full `TEST=1 ./build-everything.sh` (all 24 jars) is clean before the PR, or you list exactly what failed.

## Branch
`handoff/session-4-moderation`, branched from `main` **after session 2 is merged** (and after session 3 if that is already merged). Open a pull request to `main`.

## Report back
The final ladder format with examples, every new command and permission, file formats of `strikes.json`, `mutes.json`, `history/*.jsonl`, default values, test results per loader/version
including the restart-persistence test, privacy decisions, limits (for example tempban depends on an external ban command), open issues.
